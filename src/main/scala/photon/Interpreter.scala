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
    core.macroHandler(CallContext(this, shouldTryToPartiallyEvaluate = true, isInPartialEvaluation = false), name, parser)

  def evaluate(value: Value): Value =
    evaluate(AssignmentTransform.transform(value, None), core.rootScope, shouldTryToPartiallyEvaluate = true, isInPartialEvaluation = false)

  def evaluate(value: Value, scope: Scope, shouldTryToPartiallyEvaluate: Boolean, isInPartialEvaluation: Boolean): Value = {
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
        // TODO: How should this reference the same struct after the evaluation?
//        val scopeWithSelf = Scope(
//          Some(scope),
//          Map(("self", ))
//        )
        val evaluatedProperties = properties
          .map { case (key, value) => (key, evaluate(value, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation)) }

        Value.Struct(
          Struct(evaluatedProperties),
          location
        )

      case Value.Lambda(Lambda(params, _, body), location) =>
        if (shouldTryToPartiallyEvaluate) {
          val evalScope = Scope(
            Some(scope),
            params.map { parameter => (parameter.name, Value.Unknown(None)) }.toMap
          )

          Value.Lambda(
            Lambda(params, Some(scope), evaluate(body, evalScope, shouldTryToPartiallyEvaluate, isInPartialEvaluation = true)),
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
          .map(evaluate(_, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation))
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
              callMethod(lambda, "call", arguments, scope, location, shouldEvalTarget = false, shouldTryToPartiallyEvaluate, isInPartialEvaluation)
            case Some(value) =>
              callMethod(value, "call", arguments, scope, location, shouldEvalTarget = false, shouldTryToPartiallyEvaluate, isInPartialEvaluation)
              // evalError(value, "Cannot call this object as a function")
            case None =>
              callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, shouldTryToPartiallyEvaluate, isInPartialEvaluation)
          }
        } else {
          callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, shouldTryToPartiallyEvaluate, isInPartialEvaluation)
        }
    }
  }

  private def callMethod(
    target: Value,
    name: String,
    arguments: Arguments,
    scope: Scope,
    location: Option[Location],
    shouldEvalTarget: Boolean = false,
    shouldTryToPartiallyEvaluate: Boolean,
    isInPartialEvaluation: Boolean
  ): Value = {
    var targetWasLambda = false
    val evalTarget = if (shouldEvalTarget) {
      target match {
        case Value.Operation(_, _) => evaluate(target, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation)
        case Value.Lambda(Lambda(params, _, body), l) =>
          targetWasLambda = true
          Value.Lambda(Lambda(params, Some(scope), body), l)
        case _ => target
      }
    } else target

    val evaledPositionalArguments = arguments.positional.map(evaluate(_, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation))
    val evaledNamedArguments = arguments.named.view.mapValues(evaluate(_, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation))
    val evaledArguments = Arguments(evaledPositionalArguments, evaledNamedArguments.toMap)

    val shouldTryToEvaluate = evalTarget.isStatic && evaledPositionalArguments.forall(_.isStatic) && evaledNamedArguments.values.forall(_.isStatic)

    if (shouldTryToEvaluate) {
      logger.debug(s"Can evaluate $evalTarget.$name(${Unparser.unparse(evaledArguments)})")
    } else {
      logger.debug(s"Cannot evaluate $evalTarget.$name(${Unparser.unparse(evaledArguments)})")
    }

    if (shouldTryToEvaluate) {
      val call = Value.Operation(Operation.Call(
        name = name,
        target = evalTarget,
        arguments = evaledArguments,
        mayBeVarCall = false
      ), location)

      logger.debug(s"Will evaluate call $call in $scope")

      val context = CallContext(this, shouldTryToPartiallyEvaluate = shouldTryToPartiallyEvaluate, isInPartialEvaluation = isInPartialEvaluation)

      Core.nativeValueFor(evalTarget).method(context, name, location) match {
        case Some(method) =>
          if (isInPartialEvaluation && method.withSideEffects) {
            logger.debug(s"Not partially evaluating $call because it has side-effects")
            call
          } else {
            // TODO: Add self as a separate parameter?
            val result = method.call(context, Arguments(evalTarget +: evaledArguments.positional, evaledArguments.named), location)

            logger.debug(s"$call -> $result")

            result
          }

        case None => throw EvalError(s"Cannot call method $name on ${evalTarget.toString}", location)
      }
    } else {
      val partiallyEvaluatedTarget = if (shouldTryToPartiallyEvaluate && shouldEvalTarget && targetWasLambda) {
        evalTarget match {
          case Value.Lambda(_, _) => evaluate(evalTarget, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation = true)
          case _ => evalTarget
        }
      } else evalTarget

      val call = Value.Operation(Operation.Call(
        name = name,
        target = partiallyEvaluatedTarget,
        arguments = evaledArguments,
        mayBeVarCall = false
      ), location)

      call
    }
  }

  private def evaluate(block: Operation.Block, scope: Scope, shouldTryToPartiallyEvaluate: Boolean, isInPartialEvaluation: Boolean): Operation.Block =
    Operation.Block(
      block.values.map(evaluate(_, scope, shouldTryToPartiallyEvaluate, isInPartialEvaluation))
    )

  private def evalError(value: Value, message: String): Nothing = {
    throw EvalError(s"Cannot evaluate ${value.inspect}. $message".strip(), value.location)
  }
}
