package photon.frontend

import photon.interpreter.EvalError
import photon.{Arguments, Location, Scope, UFunction, ULiteral, UOperation, UParameter, UValue, VariableName}

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): UValue = {
    ast match {
      case ASTValue.Boolean(value, location) => ULiteral.Boolean(value, location)
      case ASTValue.Int(value, location) => ULiteral.Int(value, location)
      case ASTValue.Float(value, location) => ULiteral.Float(value, location)
      case ASTValue.String(value, location) => ULiteral.String(value, location)

      case ASTValue.Block(values, location) =>
        UOperation.Block(values.map(transform(_, scope)), location)

      case ASTValue.Function(params, astBody, returnType, location) =>
        val parameters = params.map { case ASTParameter(name, typeValue, location) =>
          val utype = typeValue.map(transform(_, scope)).getOrElse {
            throw EvalError("Function parameter types have to be defined explicitly for now", location)
          }

          UParameter(new VariableName(name), utype, location)
        }

        val lambdaScope = scope.newChild(parameters.map(_.name))

        val body = transform(astBody, lambdaScope)
        val returns = returnType.map(transform(_, scope))
        val fn = new UFunction(parameters, body, returns)

        UOperation.Function(fn, location)

      case ASTValue.Call(target, name, astArguments, mayBeVarCall, location) =>
        val positionalArgs = astArguments.positional.map(transform(_, scope))
        val namedArgs = astArguments.named.map { case (name, astValue) => (name, transform(astValue, scope)) }

        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) =>
              val target = UOperation.Reference(value, location)

              return UOperation.Call(
                "call",
                Arguments(target, positionalArgs, namedArgs),
                location
              )

            case _ =>
          }
        }

        val utarget = transform(target, scope)

        UOperation.Call(
          name,
          Arguments(utarget, positionalArgs, namedArgs),
          location
        )

      case ASTValue.NameReference(name, location) =>
        scope.find(name) match {
          case Some(variable) =>
            UOperation.Reference(variable, location)

          case None =>
            val self = scope.find("self").getOrElse { throw EvalError("Cannot find 'self' in scope", location) }
            val referenceToSelf = UOperation.Reference(self, location)

            UOperation.Call(
              name = name,
              arguments = Arguments.empty(referenceToSelf),
              location
            )
        }

      case ASTValue.Let(name, value, block, location) =>
        val variable = new VariableName(name)
        val innerScope = scope.newChild(Seq(variable))
        val expression = transform(value, innerScope)
        val body = transform(block, innerScope)

        UOperation.Let(variable, expression, body, location)
    }
  }

//  def transformBlock(block: ASTBlock, scope: StaticScope, location: Option[Location]): UOperation.Block = {
//    UOperation.Block(
//      block.values.map(transform(_, scope)),
//      location
//    )
//  }
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
