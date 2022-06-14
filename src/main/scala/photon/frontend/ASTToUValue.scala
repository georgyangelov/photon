package photon.frontend

import photon.base._

object ASTToUValue {
  def transform(ast: ASTValue, scope: StaticScope): UValue = {
    ast match {
      case ASTValue.Boolean(value, location) => ULiteral.Boolean(value, location)
      case ASTValue.Int(value, location) => ULiteral.Int(value, location)
      case ASTValue.Float(value, location) => ULiteral.Float(value, location)
      case ASTValue.String(value, location) => ULiteral.String(value, location)

      case ASTValue.Block(values, location) =>
        UOperation.Block(values.map(transform(_, scope)), location)

      case ASTValue.Function(params, astBody, returnType, location) =>
        val (parameters, lambdaScope) = transform(params, scope)

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
            val self = scope.find("self").getOrElse { throw EvalError(s"Cannot find name $name", location) }
            val referenceToSelf = UOperation.Reference(self, location)

            UOperation.Call(
              name = name,
              arguments = Arguments.empty(referenceToSelf),
              location
            )
        }

      case ASTValue.Let(name, value, block, location) =>
        val variable = new VarName(name)
        val innerScope = scope.newChild(Map(name -> variable))
        val expression = transform(value, innerScope)
        val body = transform(block, innerScope)

        UOperation.Let(variable, expression, body, location)

      case pattern: ASTValue.Pattern => transform(pattern, scope)
    }
  }

  def transform(
    params: Seq[ASTParameter],
    scope: StaticScope
  ): (Seq[UParameter], StaticScope) = {
    val uparams = Seq.newBuilder[UParameter]
    var resultScope = scope

    params.foreach { param =>
      val (upattern, newPatternScope) = param.typePattern.map(transform(_, resultScope)).getOrElse {
        throw EvalError("Function parameter types have to be defined explicitly for now", param.location)
      }

      val uparam = UParameter(param.outName, new VarName(param.inName), upattern, param.location)

      uparams.addOne(uparam)

      resultScope = newPatternScope.newChild(
        Map(param.inName -> uparam.inName)
      )
    }

    (uparams.result, resultScope)
  }

  def transform(ast: ASTValue.Pattern, scope: StaticScope): (UPattern, StaticScope) = {
    ast match {
      case ASTValue.Pattern.SpecificValue(value, location) =>
        (UPattern.SpecificValue(transform(value, scope), location), scope)

      case ASTValue.Pattern.Binding(name, location) =>
        val varName = new VarName(name)

        (UPattern.Binding(varName, location), scope.newChild(Map(name -> varName)))

      case ASTValue.Pattern.Call(target, name, args, mayBeVarCall, location) =>
        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) =>
              val utarget = UOperation.Reference(value, location)
              val (uargs, resultScope) = transform(args, scope)

              return (UPattern.Call(utarget, "call", uargs, location), resultScope)

            case _ =>
          }
        }

        val utarget = transform(target, scope)
        val (uargs, resultScope) = transform(args, scope)

        (UPattern.Call(utarget, name, uargs, location), resultScope)
    }
  }

  def transform(ast: ArgumentsWithoutSelf[ASTValue.Pattern], scope: StaticScope): (ArgumentsWithoutSelf[UPattern], StaticScope) = {
    var resultScope = scope
    val patterns = ast.map { pattern =>
      val (resultPattern, newScope) = transform(pattern, resultScope)

      resultScope = newScope

      resultPattern
    }

    (patterns, resultScope)
  }
}