package photon

import com.typesafe.scalalogging.Logger
import photon.core.{CallContext, Core}
import transforms._

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

class Interpreter() {
  private val logger = Logger[Interpreter]
  private val core = new Core

  def macroHandler(name: String, parser: Parser): Option[Value] =
    core.macroHandler(CallContext(this, compileTime = true, partial = false), name, parser)

  def evaluate(value: Value): Value =
    evaluate(AssignmentTransform.transform(value, None), core.rootScope, compileTime = true, partial = false)

  def evaluate(value: Value, scope: Scope, compileTime: Boolean, partial: Boolean): Value = {
    logger.debug(s"Evaluating $value in $scope")

    value match {
      case
        Value.Unknown(_) |
        Value.Nothing(_) |
        Value.Boolean(_, _) |
        Value.Int(_, _) |
        Value.Float(_, _) |
        Value.String(_, _) |
        Value.Native(_, _) => value

      case Value.Struct(Struct(properties), location) =>
        val evaluatedProperties = properties
          .map { case (key, value) => (key, evaluate(value, scope, compileTime, partial)) }

        Value.Struct(
          Struct(evaluatedProperties),
          location
        )

      case Value.Lambda(Lambda(params, _, body), location) =>
        if (compileTime) {
          val evalScope = Scope(
            Some(scope),
            params.map((_, Value.Unknown(None))).toMap
          )

          Value.Lambda(
            Lambda(params, Some(scope), evaluate(body, evalScope, compileTime, partial = true)),
            location
          )
        } else {
          Value.Lambda(
            Lambda(params, Some(scope), body),
            location
          )
        }

      case Value.Operation(Operation.Block(values), location) =>
        if (values.isEmpty) {
          return Value.Operation(Operation.Block(Seq.empty), location)
        }

        val lastIndex = values.size - 1

        val newValues = values
          .map(evaluate(_, scope, compileTime, partial))
          .zipWithIndex
          .filter { case (value, index) => value.isOperation || index == lastIndex }
          .map { case (value, _) => value }

        val result = if (newValues.size == 1) {
          newValues.last
        } else {
          Value.Operation(Operation.Block(newValues), location)
        }

        logger.debug(s"Evaluated $value to $result")

        result

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
            case Some(lambda @ Value.Lambda(_, _)) =>
              callMethod(lambda, "call", arguments, scope, location, shouldEvalTarget = false, compileTime, partial)
            case Some(value) => evalError(value, "Cannot call this object as a function")
            case None =>
              callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, compileTime, partial)
          }
        } else {
          callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, compileTime, partial)
        }
    }
  }

  private def callMethod(
    target: Value,
    name: String,
    arguments: Seq[Value],
    scope: Scope,
    location: Option[Location],
    shouldEvalTarget: Boolean = false,
    compileTime: Boolean,
    partial: Boolean
  ): Value = {
    val evalTarget = if (shouldEvalTarget) {
      target match {
        case Value.Operation(_, _) => evaluate(target, scope, compileTime, partial)
        case Value.Lambda(Lambda(params, _, body), l) => Value.Lambda(Lambda(params, Some(scope), body), l)
        case _ => target
      }
    } else target

    val evalArguments = arguments.map(evaluate(_, scope, compileTime, partial))
    val shouldTryToEvaluate = evalTarget.isStatic && evalArguments.forall(_.isStatic)

    if (shouldTryToEvaluate) {
      logger.debug(s"Can evaluate $evalTarget.$name(${evalArguments.mkString(", ")})")
    } else {
      logger.debug(s"Cannot evaluate $evalTarget.$name(${evalArguments.mkString(", ")})")
    }

    val call = Value.Operation(Operation.Call(
      name = name,
      target = evalTarget,
      arguments = evalArguments,
      mayBeVarCall = false
    ), location)

    if (shouldTryToEvaluate) {
      logger.debug(s"Will evaluate call $call in $scope")

      val context = CallContext(this, compileTime = compileTime, partial = partial)

      Core.nativeValueFor(evalTarget).method(context, name, location) match {
        case Some(method) =>
          if (partial && method.withSideEffects) {
            logger.debug(s"Not partially evaluating $call because it has side-effects")
            call
          } else {
            val result = method.call(context, evalTarget +: evalArguments, location)

            logger.debug(s"$call -> $result")

            result
          }

        case None => throw EvalError(s"Cannot call method $name on ${evalTarget.toString}", location)
      }
    } else {
      call
    }
  }

  private def evaluate(block: Operation.Block, scope: Scope, compileTime: Boolean, partial: Boolean): Operation.Block =
    Operation.Block(
      block.values.map(evaluate(_, scope, compileTime, partial))
    )

  private def evalError(value: Value, message: String): Nothing = {
    throw EvalError(s"Cannot evaluate ${value.inspect}. $message".strip(), value.location)
  }
}
