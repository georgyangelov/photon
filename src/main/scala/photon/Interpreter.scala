package photon

import photon.core.{CallContext, Core}
import transforms._

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

case class Scope(parent: Option[Scope], values: Map[String, Value]) {
  def find(name: String): Option[Value] = {
    values
      .get(name)
      .orElse { parent.flatMap(_.find(name)) }
  }
}

class Interpreter() {
  private val rootScope = Scope(None, Map.empty)

  def evaluate(value: Value): Value =
    evaluate(AssignmentTransform.transform(value), rootScope)

  def evaluate(value: Value, scope: Scope = rootScope): Value = {
    value match {
      case
        Value.Unknown(_) |
        Value.Nothing(_) |
        Value.Boolean(_, _) |
        Value.Int(_, _) |
        Value.Float(_, _) |
        Value.String(_, _) => value

      case Value.Struct(Struct(properties), location) =>
        val evaluatedProperties = properties
          .map { case (key, value) => (key, evaluate(value, scope)) }

        Value.Struct(
          Struct(evaluatedProperties),
          location
        )

      case Value.Lambda(Lambda(params, _, body), location) =>
        val evalScope = Scope(
          Some(scope),
          params.map((_, Value.Unknown(None))).toMap
        )

        Value.Lambda(
          Lambda(params, Some(scope), evaluate(body, evalScope)),
          location
        )

      case Value.Operation(Operation.Block(values), location) =>
        if (values.isEmpty) {
          return Value.Operation(Operation.Block(Seq.empty), location)
        }

        val lastIndex = values.size - 1

        val newValues = values.view
          .map(evaluate(_, scope))
          .zipWithIndex
          .filter { case (value, index) => value.isOperation || index == lastIndex }
          .map { case (value, _) => value }

        if (newValues.size == 1) {
          newValues.last
        } else {
          Value.Operation(Operation.Block(newValues.toSeq), location)
        }

      case Value.Operation(Operation.NameReference(name), _) =>
        scope.find(name) match {
          case Some(found) =>
            if (found.isUnknown) {
              value
            } else {
              found
            }

          case None => evalError(value, s"Cannot find name '$name'")
        }

      case Value.Operation(Operation.Assignment(_, _), _) =>
        evalError(value, "This should have been transformed to a lambda call")

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(Value.Unknown(_)) => value
            case Some(lambda @ Value.Lambda(_, _)) => callLambda(lambda, arguments, scope)
            case None => callMethod(target, name, arguments, scope)
          }
        } else {
          callMethod(target, name, arguments, scope)
        }
    }
  }

  private def callMethod(
    target: Value,
    name: String,
    arguments: Seq[Value],
    scope: Scope
  ): Value = {
    val evalTarget = evaluate(target, scope)
    val evalArguments = arguments.map(evaluate(_, scope))
    val nativeObject = Core.nativeObjectFor(evalTarget)

    nativeObject.call(CallContext(this), name, evalTarget +: evalArguments, target.location)
  }

  private def callLambda(
    lambda: Value.Lambda,
    arguments: Seq[Value],
    scope: Scope
  ): Value = {
    val evalArguments = arguments.map(evaluate(_, scope))
    val nativeObject = Core.nativeObjectFor(lambda)

    nativeObject.call(CallContext(this), "call", lambda +: evalArguments, lambda.location)
  }

  private def evaluate(block: Operation.Block, scope: Scope): Operation.Block =
    Operation.Block(
      block.values.map(evaluate(_, scope))
    )

  private def evalError(value: Value, message: String): Nothing = {
    throw EvalError(s"Cannot evaluate ${value.inspect}. $message".strip(), value.location)
  }
}
