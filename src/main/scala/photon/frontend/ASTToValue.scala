package photon.frontend

import photon.base._
import photon.core._
import photon.core.objects._
import photon.core.operations._

object ASTToValue {
  def transform(ast: ASTValue, scope: StaticScope): Value = ast match {
    case ASTValue.Boolean(value, location) => ???
    case ASTValue.Int(value, location) => $Object(value, $Int, location)
    case ASTValue.Float(value, location) => ???
    case ASTValue.String(value, location) => ???
    case ASTValue.Block(values, location) => $Block(values.map(transform(_, scope)), location)
    case ASTValue.Function(params, body, returnType, location) => ???

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
}
