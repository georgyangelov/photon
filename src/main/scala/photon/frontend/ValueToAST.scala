package photon.frontend

import photon.core.MacroASTValue
import photon.{Arguments, Function, Location, Operation, RealValue, Value, VariableName}

object ValueToAST {
  def transformRenamingAll(value: Value, prefix: String): ASTValue =
    transform(value, Map.empty, renameAllPrefix = Some(prefix))

  def transform(value: Value, varNames: Map[VariableName, String]): ASTValue =
    transform(value, varNames, renameAllPrefix = None)

  def transform(
    value: Value,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String]
  ): ASTValue = value match {
    case Value.Real(value, location) =>
      transformRealValue(value, location, varNames, renameAllPrefix)

    case Value.Operation(operation, location) =>
      transformOperation(operation, location, varNames, renameAllPrefix)
  }

  def transformRealValue(
    realValue: RealValue,
    location: Option[Location],
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String]
  ): ASTValue = realValue match {
    case RealValue.Nothing => ASTValue.NameReference("None", location)
    case RealValue.Boolean(value) => ASTValue.Boolean(value, location)
    case RealValue.Int(value) => ASTValue.Int(value, location)
    case RealValue.Float(value) => ASTValue.Float(value, location)
    case RealValue.String(value) => ASTValue.String(value, location)
    case RealValue.Native(MacroASTValue(ast)) => ast

    // TODO: Separate values for inspection and for actual AST generation
    case RealValue.Native(native) => ASTValue.NameReference("<native>", location)

    // TODO: Unbind, then transform?
    case RealValue.BoundFn(boundFn) => transformFn(boundFn.fn, varNames, location)
  }

  def transformOperation(
    operation: Operation,
    location: Option[Location],
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String]
  ): ASTValue = operation match {
    case Operation.Block(values, _) =>
      ASTValue.Block(
        ASTBlock(values.map(transform(_, varNames, renameAllPrefix))),
        location
      )

    case Operation.Let(variable, letValue, block, _) =>
      val name = renameAllPrefix match {
        case Some(prefix) => s"${prefix}$$${variable.originalName}"
        case None =>
          uniqueName(
            variable.originalName,
            letValue.unboundNames.map(_.originalName)
          )
      }

      val innerVarNames = varNames.updated(variable, name)

      ASTValue.Let(
        name,
        transform(letValue, innerVarNames, renameAllPrefix),
        transformBlock(block, innerVarNames),
        location
      )

    case Operation.Reference(name, _) =>
      ASTValue.NameReference(
        varNames.getOrElse(name, name.originalName),
        location
      )

    case Operation.Function(fn, _) =>
      transformFn(fn, varNames, location)

    case Operation.Call(target, name, arguments, _) =>
      val astTarget = transform(target, varNames, renameAllPrefix)
      val astArguments = ASTArguments(
        positional = arguments.positional.map(transform(_, varNames, renameAllPrefix)),

        // TODO: Support renames of function parameters
        named = arguments.named.view.mapValues(transform(_, varNames, renameAllPrefix)).toMap
      )

      astTarget match {
        case ASTValue.NameReference(targetName, _) if name == "call" =>
          ASTValue.Call(
            target = ASTValue.NameReference("self", location),
            name = targetName,
            arguments = astArguments,
            mayBeVarCall = true,
            location = location
          )
        case _ =>
          ASTValue.Call(
            target = astTarget,
            name = name,
            arguments = astArguments,
            mayBeVarCall = false,
            location = location
          )
      }
  }

  def transformAsBlock(value: Value): ASTBlock = {
    value match {
      case Value.Operation(Operation.Block(values, _), _) => ASTBlock(values.map(transform(_, Map.empty)))
      case _ => ASTBlock(Seq(transform(value, Map.empty)))
    }
  }

  def transformForInspection(value: Value): ASTBlock = {
    value match {
      case Value.Operation(Operation.Block(values, _), _) => ASTBlock(values.map(transform(_, Map.empty)))
      case _ => ASTBlock(Seq(transform(value, Map.empty)))
    }
  }

  def transformForInspection(value: RealValue): ASTBlock =
    ASTBlock(Seq(transformRealValue(value, None, Map.empty, None)))

  def transformForInspection(operation: Operation): ASTBlock =
    ASTBlock(Seq(transformOperation(operation, None, Map.empty, None)))

  def transformForInspection(arguments: Arguments) = ASTArguments(
    positional = arguments.positional.map(transform(_, Map.empty)),
    named = arguments.named.map { case (name, value) => (name, transform(value, Map.empty)) }
  )

  private def uniqueName(original: String, used: Set[String]) = {
    var name = original
    var i = 1

    while (used.contains(name)) {
      name = s"${original}__$i"
      i += 1
    }

    name
  }

  private def transformBlock(block: Operation.Block, varNames: Map[VariableName, String]) =
    ASTBlock(block.values.map(transform(_, varNames)))

  private def transformFn(fn: Function, varNames: Map[VariableName, String], location: Option[Location]) =
    ASTValue.Function(
      params = fn.params.map { param =>
        ASTParameter(
          // TODO: Support renames of function parameters
          name = param.name.originalName,
          typeValue = param.typeValue.map(transform(_, varNames))
        )
      },
      body = transformBlock(fn.body, varNames),
      location
    )
}
