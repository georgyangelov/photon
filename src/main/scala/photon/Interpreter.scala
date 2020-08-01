package photon

import com.typesafe.scalalogging.Logger
import photon.core.{CallContext, Core}
import transforms._

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

class Interpreter() {
  private val logger = Logger[Interpreter]
  private val core = new Core
  private val analysis = new Analysis

  def macroHandler(name: String, parser: Parser): Option[Value] =
    core.macroHandler(CallContext(this), name, parser)

  def evaluate(value: Value): Value =
    evaluate(AssignmentTransform.transform(value), core.rootScope, partial = true)

  def evaluate(value: Value, scope: Scope, partial: Boolean = false): Value = {
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
          .map { case (key, value) => (key, evaluate(value, scope, partial)) }

        Value.Struct(
          Struct(evaluatedProperties),
          location
        )

      case Value.Lambda(Lambda(params, _, body), location) =>
        if (partial) {
          val evalScope = Scope(
            Some(scope),
            params.map((_, Value.Unknown(None))).toMap
          )

          Value.Lambda(
            Lambda(params, Some(scope), evaluate(body, evalScope, partial)),
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
          .map(evaluate(_, scope, partial))
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
              callMethod(lambda, "call", arguments, scope, location)
            case Some(value) => evalError(value, "Cannot call this object as a function")
            case None =>
              callMethod(target, name, arguments, scope, location, shouldEvalTarget = true)
          }
        } else {
          callMethod(target, name, arguments, scope, location, shouldEvalTarget = true)
        }
    }
  }

  // TODO: Make sure to not change the IDs of the values when not changing the values
  //       This will make the cache of Analysis behave better
  private def evaluatePartially(value: Value, scope: Scope): Value = {
    value match {
      case Value.Unknown(_) |
           Value.Nothing(_) |
           Value.Boolean(_, _) |
           Value.Int(_, _) |
           Value.Float(_, _) |
           Value.String(_, _) |
           Value.Native(_, _) => value

      case Value.Struct(Struct(props), location) =>
        val evalProps = props.map { case (key, value) => (key, evaluatePartially(value, scope)) }

        Value.Struct(Struct(evalProps), location)

      case Value.Lambda(Lambda(params, _, body), location) =>
        val innerScope = Scope(Some(scope), params.map((_, Value.Unknown(location))).toMap)
        val evalBody = evaluatePartially(body, innerScope)

        Value.Lambda(Lambda(params, Some(scope), evalBody), location)

      case Value.Operation(block @ Operation.Block(_), location) => Value.Operation(evaluatePartially(block, scope), location)
      case Value.Operation(Operation.NameReference(_), _) => value
      case Value.Operation(Operation.Assignment(_, _), _) =>
        evalError(value, "This should have been transformed to a lambda call")

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        // TODO: What about var call?

        if (analysis.isStatic(target) && arguments.forall(analysis.isStatic)) {
          // Call the function
          // TODO: This `callMethod` does the same isStatic checks itself, should we use that or create another?
          callMethod(target, name, arguments, scope, location, shouldEvalTarget = true)
        } else {
          // Just partially evaluate the arguments and the target

        }
    }
  }

  private def evaluatePartially(block: Operation.Block, scope: Scope): Operation.Block =
    Operation.Block(
      block.values.map(evaluatePartially(_, scope))
    )

//  private def evaluatePartially(value: Value, scope: Scope): Value = {
//    value match {
//      case Value.Unknown(_) |
//           Value.Nothing(_) |
//           Value.Boolean(_, _) |
//           Value.Int(_, _) |
//           Value.Float(_, _) |
//           Value.String(_, _) |
//           Value.Native(_, _) => value
//
//      case Value.Struct(Struct(props), location) =>
//        val evalProps = props.map { case (key, value) => (key, evaluatePartially(value, scope)) }
//
//        Value.Struct(Struct(evalProps), location)
//
//      case Value.Lambda(Lambda(params, _, body), location) =>
//        val innerScope = Scope(Some(scope), params.map((_, Value.Unknown(location))).toMap)
//        val evalBody = evaluatePartially(body, innerScope)
//
//        Value.Lambda(Lambda(params, Some(scope), evalBody), location)
//
//      case Value.Operation(Operation.Block(body), location) =>
//      case Value.Operation(Operation.NameReference(name), location) =>
//      case Value.Operation(Operation.Assignment(name, value), location) => ???
//      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
//
//    }
//  }

//  private def evaluatePartially(block: Operation.Block, scope: Scope): Operation.Block = {
//
//  }

  private def callMethod(
    target: Value,
    name: String,
    arguments: Seq[Value],
    scope: Scope,
    location: Option[Location],
    shouldEvalTarget: Boolean = false
  ): Value = {
    val evalTarget = if (shouldEvalTarget) {
      target match {
        case Value.Operation(_, _) => evaluate(target, scope, partial = false)
        case Value.Lambda(Lambda(params, _, body), l) => Value.Lambda(Lambda(params, Some(scope), body), l)
        case _ => target
      }
    } else target

    val evalArguments = arguments.map(evaluate(_, scope, partial = false))
    val canEvaluate = evalTarget.isStatic && evalArguments.forall(_.isStatic)

    if (canEvaluate) {
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

    if (canEvaluate) {
      logger.debug(s"Will evaluate call $call in $scope")

      val result = Core
        .nativeValueFor(evalTarget)
        .call(CallContext(this), name, evalTarget +: evalArguments, location)

      logger.debug(s"$call -> $result")

      result
    } else {
      Value.Operation(Operation.Call(
        target = evalTarget,
        name = name,
        arguments = evalArguments,
        mayBeVarCall = false
      ), location)
    }
  }

  private def evaluate(block: Operation.Block, scope: Scope, partial: Boolean): Operation.Block =
    Operation.Block(
      block.values.map(evaluate(_, scope, partial))
    )

  private def evalError(value: Value, message: String): Nothing = {
    throw EvalError(s"Cannot evaluate ${value.inspect}. $message".strip(), value.location)
  }
}
