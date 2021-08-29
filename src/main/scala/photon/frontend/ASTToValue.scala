package photon.frontend

import photon.interpreter.EvalError
import photon.{Arguments, Function, Operation, Parameter, RealValue, Scope, Value, VariableName}

import scala.collection.Map

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = {
    ast match {
      case ASTValue.Boolean(value, location) => Value.Real(RealValue.Boolean(value), location)
      case ASTValue.Int(value, location) => Value.Real(RealValue.Int(value), location)
      case ASTValue.Float(value, location) => Value.Real(RealValue.Float(value), location)
      case ASTValue.String(value, location) => Value.Real(RealValue.String(value), location)

      case ASTValue.Block(block, location) =>
        Value.Operation(
          Operation.Block(block.values.map(transform(_, scope)), None),
          location
        )

      case ASTValue.Function(params, astBody, location) =>
        val parameters = params.map { case ASTParameter(name, typeValue) =>
          Parameter(new VariableName(name), typeValue.map(transform(_, scope)))
        }

        val lambdaScope = scope.newChild(parameters.map(_.name))

        val body = transformBlock(astBody, lambdaScope)
        val fn = new Function(parameters, body)

        Value.Operation(
          Operation.Function(fn, None),
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
                  target = Value.Operation(Operation.Reference(value, None), location),
                  name = "call",
                  arguments = arguments,
                  None
                ),
                location
              )

            case _ =>
          }
        }

        Value.Operation(
          Operation.Call(transform(target, scope), name, arguments, None),
          location
        )

      case ASTValue.NameReference(name, location) =>
        scope.find(name) match {
          case Some(variable) =>
            Value.Operation(
              Operation.Reference(variable, None),
              location
            )

          case None =>
            val self = scope.find("self").getOrElse { throw EvalError("Cannot find 'self' in scope", location) }

            Value.Operation(
              Operation.Call(
                target = Value.Operation(Operation.Reference(self, None), location),
                name = name,
                arguments = Arguments.empty,
                None
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
          Operation.Let(variable, expression, body, None),
          location
        )
    }
  }

  def transformBlock(block: ASTBlock, scope: StaticScope): Operation.Block = {
    Operation.Block(
      block.values.map(transform(_, scope)),
      None
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

  def fromScope(scope: Scope): StaticScope = {
    val parentStaticScope = scope.parent.map(StaticScope.fromScope)
    val variables = scope.variables.keys
      .map { variable => (variable.originalName, variable) }
      .toMap

    StaticScope(parentStaticScope, variables)
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
