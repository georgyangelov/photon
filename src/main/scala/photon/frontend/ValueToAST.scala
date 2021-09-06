package photon.frontend

import photon.core.MacroASTValue
import photon.interpreter.EvalError
import photon.{Arguments, BoundValue, Function, Location, Operation, PureValue, UnboundValue, Value, VariableName}
import photon.core.List

object ValueToAST {
  def transformRenamingAll(value: UnboundValue, prefix: String): ASTValue =
    transform(value, Map.empty, renameAllPrefix = Some(prefix), forInspection = false)

  def transform(value: UnboundValue): ASTValue =
    transform(value, Map.empty, renameAllPrefix = None, forInspection = false)

  def transformAsBlock(value: UnboundValue): ASTBlock = {
    value match {
      case Operation.Block(values, _, _) => ASTBlock(values.map(transform(_, Map.empty, None, forInspection = false)))
      case _ => ASTBlock(Seq(transform(value, Map.empty, None, forInspection = false)))
    }
  }

  def transformForInspection(value: Value): ASTBlock =
    transform(value, Map.empty, renameAllPrefix = None, forInspection = true) match {
      case ASTValue.Block(block, _) => block
      case astValue => ASTBlock(Seq(astValue))
    }

  def transformForInspection(arguments: Arguments[Value]) = ASTArguments(
    positional = arguments.positional.map(transform(_, Map.empty, None, forInspection = true)),
    named = arguments.named.view.mapValues(transform(_, Map.empty, None, forInspection = true)).toMap
  )

  private def transform(
    value: Value,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String],
    forInspection: Boolean
  ): ASTValue = value match {
    case PureValue.Nothing(location) => ASTValue.NameReference("None", location)
    case PureValue.Boolean(value, location) => ASTValue.Boolean(value, location)
    case PureValue.Int(value, location) => ASTValue.Int(value, location)
    case PureValue.Float(value, location) => ASTValue.Float(value, location)
    case PureValue.String(value, location) => ASTValue.String(value, location)

    case PureValue.Native(MacroASTValue(ast), _) => ast

    case PureValue.Native(List(values), location) => ASTValue.Call(
      target = ASTValue.NameReference("List", location),
      name = "of",
      arguments = ASTArguments.positional(
        values.map(transform(_, varNames, renameAllPrefix, forInspection))
      ),
      mayBeVarCall = false,
      location
    )

    case PureValue.Native(_, location) =>
      if (!forInspection) {
        throw EvalError("Cannot convert Native value to ASTValue", location)
      }

      ASTValue.NameReference("<native>", location)

    case BoundValue.Function(fn, _, _, location) =>
      if (!forInspection) {
        throw EvalError("Cannot convert BoundValue.Function to ASTValue", location)
      }

      transformFn(fn, varNames, location, forInspection)

    case BoundValue.Object(values, _, location) =>
      if (!forInspection) {
        throw EvalError("Cannot convert BoundValue.Object to ASTValue", location)
      }

      ASTValue.Call(
        ASTValue.NameReference("Object", None),
        "call",
        ASTArguments(
          positional = Seq.empty,
          named = values.view.mapValues(transform(_, varNames, renameAllPrefix, forInspection)).toMap
        ),
        mayBeVarCall = false,
        location
      )

    case Operation.Block(values, _, location) =>
      ASTValue.Block(
        ASTBlock(values.map(transform(_, varNames, renameAllPrefix, forInspection))),
        location
      )

    case Operation.Let(variable, letValue, block, _, location) =>
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
        transform(letValue, innerVarNames, renameAllPrefix, forInspection),
        transformBlock(block, innerVarNames, renameAllPrefix, forInspection),
        location
      )

    case Operation.Reference(name, _, location) =>
      ASTValue.NameReference(
        varNames.getOrElse(name, name.originalName),
        location
      )

    case Operation.Function(fn, _, location) =>
      transformFn(fn, varNames, location, forInspection)

    case Operation.Call(target, name, arguments, _, location) =>
      val astTarget = transform(target, varNames, renameAllPrefix, forInspection)
      val astArguments = ASTArguments(
        positional = arguments.positional.map(transform(_, varNames, renameAllPrefix, forInspection)),

        // TODO: Support renames of function parameters
        named = arguments.named.view.mapValues(transform(_, varNames, renameAllPrefix, forInspection)).toMap
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

  private def uniqueName(original: String, used: Set[String]) = {
    var name = original
    var i = 1

    while (used.contains(name)) {
      name = s"${original}__$i"
      i += 1
    }

    name
  }

  private def transformBlock(
    block: Operation.Block,
    varNames: Map[VariableName, String],
    renameAllPrefix: Option[String],
    forInspection: Boolean
  ) =
    ASTBlock(block.values.map(transform(_, varNames, renameAllPrefix, forInspection)))

  private def transformFn(
    fn: Function,
    varNames: Map[VariableName, String],
    location: Option[Location],
    forInspection: Boolean
  ) =
    ASTValue.Function(
      params = fn.params.map { param =>
        ASTParameter(
          // TODO: Support renames of function parameters
          name = param.name.originalName,
          // TODO: Is this correct that the rename prefix is empty?
          typeValue = param.typeValue.map(transform(_, varNames, None, forInspection))
        )
      },
      // TODO: Is this correct that the rename prefix is empty?
      body = transformBlock(fn.body, varNames, None, forInspection),
      location
    )
}
