package photon.frontend

import photon.core.MacroASTValue
import photon.{Arguments, Function, Location, Operation, Value, VariableName}

object ValueToAST {
  def transformRenamingAll(value: Value, prefix: String): ASTValue =
    transform(value, Map.empty, renameAllPrefix = Some(prefix))

  def transform(value: Value, varNames: Map[VariableName, String]): ASTValue =
    transform(value, varNames, renameAllPrefix = None)

  def transform(value: Value, varNames: Map[VariableName, String], renameAllPrefix: Option[String]): ASTValue = {
    value match {
      case Value.Unknown(location) =>
        // TODO: Separate values for inspection and for actual AST generation
        // throw EvalError("Cannot represent Unknown as AST, this is a compiler bug", location)
        ASTValue.NameReference("<unknown>", location)

      // TODO: Support this in the root scope
      case Value.Nothing(location) => ASTValue.NameReference("None", location)

      case Value.Boolean(value, location) => ASTValue.Boolean(value, location)
      case Value.Int(value, location, _) => ASTValue.Int(value, location)
      case Value.Float(value, location) => ASTValue.Float(value, location)
      case Value.String(value, location) => ASTValue.String(value, location)

      case Value.Native(MacroASTValue(ast), _) => ast

      case Value.Native(_, location) =>
        // TODO: Separate values for inspection and for actual AST generation
        // throw EvalError("Cannot represent Native as AST, this is a compiler bug", location)
        ASTValue.NameReference("<native>", location)

      case Value.Struct(struct, location) =>
        ASTValue.Call(
          target = ASTValue.NameReference("self", location),
          name = "Struct",
          arguments = ASTArguments(
            positional = Seq.empty,
            named = struct.props.map { case (name, value) =>
              (name, transform(value, varNames, renameAllPrefix))
            }
          ),
          mayBeVarCall = true,
          location
        )

      case Value.BoundFunction(boundFn, location) =>
        // TODO: Serialize traits to AST
        transformFn(boundFn.fn, varNames, location)

      case Value.Operation(Operation.Block(values), location) =>
        ASTValue.Block(ASTBlock(values.map(transform(_, varNames, renameAllPrefix))), location)

      case Value.Operation(Operation.Let(variable, letValue, block), location) =>
        val name = renameAllPrefix match {
          case Some(prefix) => s"${prefix}$$${variable.originalName}"
          case None =>
            uniqueName(
              variable.originalName,
              value.unboundNames.map(_.originalName)
            )
        }

        val innerVarNames = varNames.updated(variable, name)

        ASTValue.Let(
          name,
          transform(letValue, innerVarNames, renameAllPrefix),
          transformBlock(block, innerVarNames),
          location
        )

      case Value.Operation(Operation.Reference(name), location) =>
        ASTValue.NameReference(
          varNames.getOrElse(name, name.originalName),
          location
        )

      case Value.Operation(Operation.Function(fn), location) => transformFn(fn, varNames, location)

      case Value.Operation(Operation.Call(target, name, arguments), location) =>
        val astTarget = transform(target, varNames, renameAllPrefix)
        val astArguments = ASTArguments(
          positional = arguments.positional.map(transform(_, varNames, renameAllPrefix)),

          // TODO: Support renames of function parameters
          named = arguments.named.map { case (name, value) => (name, transform(value, varNames, renameAllPrefix)) }
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
  }

  def transformAsBlock(value: Value): ASTBlock = {
    value match {
      case Value.Operation(Operation.Block(values), _) => ASTBlock(values.map(transform(_, Map.empty)))
      case _ => ASTBlock(Seq(transform(value, Map.empty)))
    }
  }

  def transformForInspection(value: Value): ASTBlock = {
    value match {
      case Value.Operation(Operation.Block(values), _) => ASTBlock(values.map(transform(_, Map.empty)))
      case _ => ASTBlock(Seq(transform(value, Map.empty)))
    }
  }

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
