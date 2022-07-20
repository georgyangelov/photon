package photon.frontend

import photon.base._
import photon.core._
import photon.core.objects._
import photon.core.operations._
import photon.frontend.ASTValue.Pattern

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = ast match {
    case ASTValue.Boolean(value, location) => ???
    case ASTValue.Int(value, location) => $Object(value, $Int, location)
    case ASTValue.Float(value, location) => ???
    case ASTValue.String(value, location) => $Object(value, $String, location)
    case ASTValue.Block(values, location) => $Block(values.map(transform(_, scope)), location)

    // TODO: Support patterns
    case ASTValue.Function(params, body, returnType, location) =>
      val fnParams = params.map(transform(_, scope))
      val innerScope = scope.newChild(
        fnParams
          .map { param => param.inName.originalName -> param.inName }
          .toMap
      )
      val fnBody = transform(body, innerScope)
      val fnReturnType = returnType.map(transform(_, scope))

      $FunctionDef(fnParams, fnBody, fnReturnType, location)

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
      val varName = scope.find(name)
        .getOrElse { throw EvalError(s"Could not find name $name in $scope", location) }

      $Reference(varName, location)

    case ASTValue.Let(name, value, block, location) =>
      val varName = new VarName(name)
      val innerScope = scope.newChild(Map(name -> varName))

      $Let(
        varName,
        transform(value, innerScope),
        transform(block, innerScope),
        location
      )

    case pattern: ASTValue.Pattern => ???
  }

  private def transform(param: ASTParameter, scope: StaticScope): Parameter = {
    val varName = new VarName(param.inName)
    val typePattern = param.typePattern
      .getOrElse { throw EvalError("Params must have explicit types for now", param.location) }

    val typ = typePattern match {
      case Pattern.SpecificValue(value) => transform(value, scope)
      case Pattern.Binding(name, location) => ???
      case Pattern.Call(target, name, args, mayBeVarCall, location) => ???
    }

    Parameter(param.outName, varName, typ, param.location)
  }
}
