package photon.frontend

import photon.{Arguments, EvalError, Function, Operation, Parameter, Value, VariableName}

import scala.collection.Map

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = {
    ast match {
      case ASTValue.Boolean(value, location) => Value.Boolean(value, location)
      case ASTValue.Int(value, location) => Value.Int(value, location, None)
      case ASTValue.Float(value, location) => Value.Float(value, location)
      case ASTValue.String(value, location) => Value.String(value, location)

      case ASTValue.Function(params, astBody, location) =>
        val parameters = params.map { case ASTParameter(name, typeValue) =>
          Parameter(new VariableName(name), typeValue.map(transform(_, scope)))
        }

        val lambdaScope = scope.newChild(parameters.map(_.name))

        val body = transformBlock(astBody, lambdaScope)
        val fn = new Function(parameters, body)

        Value.Operation(
          Operation.Function(fn),
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
                  arguments = arguments
                ),
                location
              )

            case _ =>
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

  def transformBlock(block: ASTBlock, scope: StaticScope): Operation.Block = {
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
      .mapValues { variable => s"${variable.originalName}(${variable.uniqueId})" }
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
