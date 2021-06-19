package photon.frontend

import photon.{Arguments, EvalError, Function, Location, Operation, Value, VariableName}

object ValueToAST {
  def transform(value: Value, varNames: Map[VariableName, String]): ASTValue = {
    value match {
      case Value.Unknown(location) =>
        throw EvalError("Cannot represent Unknown as AST, this is a compiler bug", location)

      // TODO: Support this in the root scope
      case Value.Nothing(location) => ASTValue.NameReference("None", location)

      case Value.Boolean(value, location) => ASTValue.Boolean(value, location)
      case Value.Int(value, location, _) => ASTValue.Int(value, location)
      case Value.Float(value, location) => ASTValue.Float(value, location)
      case Value.String(value, location) => ASTValue.String(value, location)
      case Value.Native(_, location) =>
        throw EvalError("Cannot represent Native as AST, this is a compiler bug", location)

      case Value.Struct(struct, location) =>
        ASTValue.Call(
          target = ASTValue.NameReference("Struct", location),
          name = "call",
          arguments = ASTArguments(
            positional = Seq.empty,
            named = struct.props.map { case (name, value) =>
              (name, transform(value, varNames))
            }
          ),
          mayBeVarCall = false,
          location
        )

      case Value.BoundFunction(boundFn, location) =>
        // TODO: Serialize traits to AST
        transformFn(boundFn.fn, varNames, location)

      case Value.Operation(Operation.Block(_), location) =>
        throw EvalError("Cannot represent Block as AST, this is a compiler bug", location)

      case Value.Operation(Operation.Let(variable, value, block), location) =>
        val name = uniqueName(variable.originalName, value.unboundNames.map(_.originalName))
        val innerVarNames = varNames.updated(variable, name)

        ASTValue.Let(
          name,
          transform(value, innerVarNames),
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
        ASTValue.Call(
          target = transform(target, varNames),
          name = name,
          arguments = ASTArguments(
            positional = arguments.positional.map(transform(_, varNames)),

            // TODO: Support renames of function parameters
            named = arguments.named.map { case (name, value) => (name, transform(value, varNames)) }
          ),
          mayBeVarCall = false,
          location = location
        )
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
