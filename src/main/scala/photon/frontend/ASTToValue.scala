package photon.frontend

import photon.interpreter.EvalError
import photon.{Arguments, Function, Location, Operation, Parameter, PureValue, Scope, UnboundValue, VariableName}

import scala.collection.Map

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): UnboundValue = {
    ast match {
      case ASTValue.Boolean(value, location) => PureValue.Boolean(value, location)
      case ASTValue.Int(value, location) => PureValue.Int(value, location)
      case ASTValue.Float(value, location) => PureValue.Float(value, location)
      case ASTValue.String(value, location) => PureValue.String(value, location)

      case ASTValue.Block(block, location) =>
        Operation.Block(block.values.map(transform(_, scope)), None, location)

      case ASTValue.Function(params, astBody, returnType, location) =>
        val parameters = params.map { case ASTParameter(name, typeValue, location) =>
          Parameter(new VariableName(name), typeValue.map(transform(_, scope)), location)
        }

        val selfName = new VariableName("self")
        val lambdaScope = scope.newChild(parameters.map(_.name) ++ Seq(selfName))

        val body = transformBlock(astBody, lambdaScope, location)
        val returns = returnType.map(transform(_, scope))
        val fn = new Function(selfName, parameters, body, returns)

        Operation.Function(fn, None, location)

      case ASTValue.Call(target, name, astArguments, mayBeVarCall, location) =>
        val arguments = Arguments(
          None,
          positional = astArguments.positional.map(transform(_, scope)),
          named = astArguments.named.map { case (name, astValue) => (name, transform(astValue, scope)) }
        )

        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) =>
              return Operation.Call(
                target = Operation.Reference(value, None, location),
                name = "call",
                arguments = arguments,
                None,
                location
              )

            case _ =>
          }
        }

        Operation.Call(transform(target, scope), name, arguments, None, location)

      case ASTValue.NameReference(name, location) =>
        scope.find(name) match {
          case Some(variable) =>
            Operation.Reference(variable, None, location)

          case None =>
            val self = scope.find("self").getOrElse { throw EvalError("Cannot find 'self' in scope", location) }

            Operation.Call(
              target = Operation.Reference(self, None, location),
              name = name,
              arguments = Arguments.empty,
              None,
              location
            )
        }

      case ASTValue.Let(name, value, block, location) =>
        val variable = new VariableName(name)
        val innerScope = scope.newChild(Seq(variable))
        val expression = transform(value, innerScope)
        val body = transformBlock(block, innerScope, location)

        Operation.Let(variable, expression, body, None, location)
    }
  }

  def transformBlock(block: ASTBlock, scope: StaticScope, location: Option[Location]): Operation.Block = {
    Operation.Block(
      block.values.map(transform(_, scope)),
      None,
      location
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
