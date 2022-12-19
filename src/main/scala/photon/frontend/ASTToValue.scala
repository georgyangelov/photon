package photon.frontend

import photon.base._
import photon.core._
import photon.core.objects._
import photon.core.operations._
import photon.lib.ScalaExtensions._

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = ast match {
    case ASTValue.Boolean(value, location) => $Object(value, $Boolean, location)
    case ASTValue.Int(value, location) => $Object(value, $Int, location)
    case ASTValue.Float(value, location) => ???
    case ASTValue.String(value, location) => $Object(value, $String, location)
    case ASTValue.Block(values, location) => $Block(values.map(transform(_, scope)), location)

    case fnAST @ ASTValue.Function(params, _, _, _) =>
      val isTemplateFunction = params.exists(_.typePattern match {
        case Some(_: Pattern.SpecificValue) => false
        case Some(_) => true

        // TODO: What if there is no parameter type - is that a normal function or a template one?
        case None => false
      })

      if (isTemplateFunction) {
        transformToTemplateFunction(fnAST, scope)
      } else {
        transformToNormalFunction(fnAST, scope)
      }

    case ASTValue.FunctionType(params, returnType, location) =>
      val typeParams = params.map(transform(_, scope))
      val returns = transform(returnType, scope)

      $FunctionInterfaceDef(typeParams, returns, location)

    case ASTValue.Call(target, name, arguments, mayBeVarCall, location) =>
      val positionalArgs = arguments.positional.map(transform(_, scope))
      val namedArgs = arguments.named.map { case name -> astValue => (name, transform(astValue, scope)) }

      if (mayBeVarCall) {
        scope.find(name) match {
          case Some(value) =>
            return $Call(
              "call",
              Arguments(
                self = $Reference(value, location),
                positionalArgs,
                namedArgs
              ),
              location
            )

          case _ =>
        }
      }

      $Call(
        name,
        Arguments(
          self = transform(target, scope),
          positionalArgs,
          namedArgs
        ),
        location
      )

    case ASTValue.NameReference(name, location) =>
      scope.find(name) match {
        case Some(varName) => $Reference(varName, location)
        case None =>
          val self = scope.find("self").getOrElse {
            throw EvalError(s"Could not find name $name in $scope", location)
          }
          val referenceToSelf = $Reference(self, location)

          $Call(
            name,
            args = Arguments.empty(referenceToSelf),
            location
          )
      }

    case ASTValue.Let(name, value, block, location) =>
      val varName = new VarName(name)
      val innerScope = scope.newChild(Map(name -> varName))

      $Let(
        varName,
        transform(value, innerScope),
        transform(block, innerScope),
        location
      )
  }

  private def transformToNormalFunction(ast: ASTValue.Function, scope: StaticScope): $FunctionDef = {
    val fnParams = ast.params.map(transformToSpecificParameter(_, scope))

    val innerScope = scope.newChild(
      fnParams
        .map { param => param.inName.originalName -> param.inName }
        .toMap
    )
    val fnBody = transform(ast.body, innerScope)
    val fnReturnType = ast.returnType.map(transform(_, scope))

    $FunctionDef(fnParams, fnBody, fnReturnType, ast.location)
  }

  private def transformToTemplateFunction(ast: ASTValue.Function, scope: StaticScope): $TemplateFunctionDef = {
    val (paramScope, fnParamsIterable) = ast.params.mapWithRollingContext(scope) {
      case scope -> astParam =>
        val (param, newScope) = transformToTemplateParameter(astParam, scope)

        newScope -> param
    }
    val fnParams = fnParamsIterable.toSeq

    // TODO: Switch this to `paramScope` if the fn body needs to be able to access the pattern names
    val innerScope = scope.newChild(
      fnParams
        .map { param => param.inName.originalName -> param.inName }
        .toMap
    )
    val fnBody = transform(ast.body, innerScope)
    val fnReturnType = ast.returnType.map(transform(_, paramScope))

    $TemplateFunctionDef(fnParams, fnBody, fnReturnType, ast.location)
  }

  private def transformToSpecificParameter(param: ASTParameter, scope: StaticScope): Parameter = {
    val varName = new VarName(param.inName)
    val typePattern = param.typePattern
      .getOrElse { throw EvalError("Params must have explicit types for now", param.location) }
      match {
        case Pattern.SpecificValue(value) => value

        // TODO: Mark this as not possible somehow, because we check before calling the function
        case Pattern.Binding(name, location) => ???
        case Pattern.Call(target, name, args, mayBeVarCall, location) => ???
      }

    val typ = transform(typePattern, scope)

    Parameter(param.outName, varName, typ, param.location)
  }

  private def transformToTemplateParameter(param: ASTParameter, scope: StaticScope): (TemplateFunctionParameter, StaticScope) = {
    val varName = new VarName(param.inName)
    val typePattern = param.typePattern
      .getOrElse { throw EvalError("Params must have explicit types for now", param.location) }

    val (typ, newScope) = transform(typePattern, scope)

    TemplateFunctionParameter(param.outName, varName, typ, param.location) -> newScope
  }

  private def transform(param: ASTTypeParameter, scope: StaticScope): TypeParameter = {
    val typ = transform(param.typ, scope)

    TypeParameter(param.name, typ, param.location)
  }

  private def transform(pattern: Pattern, scope: StaticScope): (ValuePattern, StaticScope) = {
    pattern match {
      case Pattern.SpecificValue(value) =>
        (
          ValuePattern.Expected(transform(value, scope), value.location),
          scope
        )

      case Pattern.Binding(name, location) =>
        val varName = new VarName(name)
        val newScope = scope.newChild(Map(name -> varName))

        (ValuePattern.Binding(varName, location), newScope)

      case Pattern.Call(target, name, args, mayBeVarCall, location) =>
        var argScope = scope
        val positional = args.positional.map { pattern =>
          val (valuePattern, newScope) = transform(pattern, argScope)

          argScope = newScope

          valuePattern
        }
        val named = args.named.view.mapValues { pattern =>
          val (valuePattern, newScope) = transform(pattern, argScope)

          argScope = newScope

          valuePattern
        }.toMap

        val valueArgs = ArgumentsWithoutSelf[ValuePattern](
          // TODO: The order of parameters matters here unfortunately, need to preserve it across positional and named,
          //       unless I want to make it so named parameters are strictly after positional ones
          positional,
          named
        )

        // TODO: Duplication with building $Call
        val (realTarget, realName) =
          if (mayBeVarCall) {
            scope.find(name) match {
              case Some(value) => $Reference(value, location) -> "call"
              case None =>
                val self = scope.find("self")
                  .getOrElse { throw EvalError(s"Could not find name $name in $scope", location) }

                $Reference(self, location) -> name
            }
          } else {
            transform(target, scope) -> name
          }

        (
          ValuePattern.Call(realTarget, realName, valueArgs, location),
          argScope
        )
    }
  }
}