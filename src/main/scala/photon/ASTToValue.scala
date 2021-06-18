package photon

import scala.collection.Map

class ASTToValue {
  case class TransformResult(value: Value, unboundNames: Set[VariableName])

  def transform(ast: ASTValue, scope: StaticScope): TransformResult = {
    ast match {
      case ASTValue.Boolean(value, location) => TransformResult(Value.Boolean(value, location), Set.empty)
      case ASTValue.Int(value, location) => TransformResult(Value.Int(value, location, None), Set.empty)
      case ASTValue.Float(value, location) => TransformResult(Value.Float(value, location), Set.empty)
      case ASTValue.String(value, location) => TransformResult(Value.String(value, location), Set.empty)
      case ASTValue.Struct(props, location) =>
        Value.Struct(
          Struct(
            props.map { case (name, astValue) => (name, transform(astValue, scope)) }
          ),
          location
        )

      case ASTValue.Lambda(params, astBody, location) =>
        val lambdaScope = scope.newChild(
          params.map(_.name).map(new VariableName(_))
        )

        val parameters = params.map { case ASTParameter(name, typeValue) => Parameter(name, typeValue.map(transform(_, scope))) }
        val body = transform(astBody, lambdaScope)
        val fn = new Function(parameters, , body)

        Value.Operation(
          Operation.Function(fn),
          location
        )

      case ASTValue.Block(values, location) =>
        Value.Operation(
          Operation.Block(values.map(transform(_, scope))),
          location
        )

      case ASTValue.Call(target, name, astArguments, mayBeVarCall, location) =>
        val arguments = Arguments(
          positional = astArguments.positional.map(transform(_, scope)),
          named = astArguments.named.map { case (name, astValue) => (name, transform(astValue, scope)) }
        )

        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) =>
              return Value.Operation(
                Operation.Call(
                  target = Value.Operation(Operation.Reference(value), location),
                  name = "call",
                  arguments = Arguments.empty
                ),
                location
              )
          }
        }

        Value.Operation(
          Operation.Call(transform(target, scope), name, arguments),
          location
        )

      case ASTValue.NameReference(name, location) =>
        scope.find(name) match {
          case Some(variable) =>
            Value.Operation(
              Operation.Reference(variable),
              location
            )

          case None =>
            val self = scope.find("self").getOrElse { throw EvalError("Cannot find 'self' in scope", location) }

            Value.Operation(
              Operation.Call(
                target = Value.Operation(Operation.Reference(self), location),
                name = name,
                arguments = Arguments.empty
              ),
              location
            )
        }

      case ASTValue.Let(name, value, block, location) =>
        val variable = new VariableName(name)
        val innerScope = scope.newChild(Seq(variable))
        val expression = transform(value, innerScope)
        val body = transformBlock(block, innerScope)

        Value.Operation(
          Operation.Let(variable, expression, body),
          location
        )
    }
  }

  def transformBlock(block: ASTValue.Block, scope: StaticScope): Operation.Block = {
    Operation.Block(
      block.values.map(transform(_, scope))
    )
  }
}

object StaticScope {
  def newRoot(variables: Seq[VariableName]): StaticScope = {
    StaticScope(
      None,
      variables.map { variable => (variable.originalName, variable) }.toMap
    )
  }
}

case class StaticScope(parent: Option[StaticScope], variables: Map[String, VariableName]) {
  def newChild(variables: Seq[VariableName]): StaticScope = {
    StaticScope(
      Some(this),
      variables.map { variable => (variable.originalName, variable) }.toMap
    )
  }

  override def toString: String = {
    val names = variables.view
      .mapValues { variable => s"${variable.originalName}(${variable.objectId})" }
      .mkString(", ")

    if (parent.isDefined) {
      s"$names -> ${parent.get.toString}"
    } else {
      names
    }
  }

  def find(name: String): Option[VariableName] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}
