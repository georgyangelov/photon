package photon

import com.typesafe.scalalogging.Logger
import photon.core.{CallContext, Core, IntRoot, NativeMethod}
import transforms._

import scala.collection.mutable

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

sealed abstract class InterpreterMode(val name: String) {
  val shouldTryToPartiallyEvaluate: Boolean
}

object InterpreterMode {
  case object PartialEvaluation extends InterpreterMode("partial") {
    override val shouldTryToPartiallyEvaluate: Boolean = true
  }

  case object CompileTime extends InterpreterMode("compile-time") {
    override val shouldTryToPartiallyEvaluate: Boolean = false
  }

  case object CompileTimeOrPartial extends InterpreterMode("compile-time-or-partial") {
    override val shouldTryToPartiallyEvaluate: Boolean = true
  }

  case object Runtime extends InterpreterMode("runtime") {
    override val shouldTryToPartiallyEvaluate: Boolean = false
  }
}

class Interpreter(val interpreterMode: InterpreterMode) {
  private val logger = Logger[Interpreter]
  private val core = new Core

  def macroHandler(name: String, parser: Parser): Option[Value] =
    core.macroHandler(CallContext(this, interpreterMode), name, parser)

  def evaluate(value: Value): Value =
    evaluate(AssignmentTransform.transform(value, None), core.rootScope, interpreterMode)

  def evaluate(value: Value, scope: Scope, mode: InterpreterMode): Value = {
    logger.debug(s"[$mode] Evaluating $value in $scope")

    value match {
      case
        Value.Unknown(_) |
        Value.Nothing(_) |
        Value.Boolean(_, _) |
        Value.Float(_, _) |
        Value.String(_, _) |
        Value.Native(_, _) => value

      case Value.Int(value, location, _) => Value.Int(value, location, Some(TypeObject.Native(IntRoot)))

      case Value.Struct(Struct(properties), location) =>
        // TODO: How should this reference the same struct after the evaluation?
//        val scopeWithSelf = Scope(
//          Some(scope),
//          Map(("self", ))
//        )
        val evaluatedProperties = properties
          .map { case (key, value) => (key, evaluate(value, scope, mode)) }

        Value.Struct(
          Struct(evaluatedProperties),
          location
        )

      case Value.Lambda(Lambda(params, _, body, traits), location) =>
        if (mode.shouldTryToPartiallyEvaluate) {
          val evalScope = Scope(
            Some(scope),
            params.map { parameter => (parameter.name, Value.Unknown(None)) }.toMap
          )

          val evalBody = evaluate(body, location, evalScope, InterpreterMode.PartialEvaluation)

          Value.Lambda(
            Lambda(params, Some(scope), evalBody, traits),
            location
          )
        } else {
          Value.Lambda(
            Lambda(params, Some(scope), body, traits),
            location
          )
        }

      case Value.Operation(Operation.Block(values), location) =>
        if (values.isEmpty) {
          return Value.Operation(Operation.Block(Seq.empty), location)
        }

        val lastIndex = values.size - 1
        val valueBuilder = Seq.newBuilder[Value]
        var evalMode = mode

        valueBuilder.sizeHint(values.size)

        values.zipWithIndex.foreach { case (value, index) =>
          val evalValue = evaluate(value, scope, evalMode)

          if (evalValue.isDynamic) {
            if (mode.shouldTryToPartiallyEvaluate) {
              evalMode = InterpreterMode.PartialEvaluation
            } else if (mode == InterpreterMode.Runtime) {
              // TODO: Test this
              throw EvalError(s"Could not evaluate $evalValue", location)
            }
          }

          if (value.isOperation || index == lastIndex) {
            valueBuilder.addOne(evalValue)
          }
        }

        val newValues = valueBuilder.result()

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
        evalError(value, "This should have been transformed to a let")

      case let @ Value.Operation(Operation.Let(name, value, block), location) =>
        val scopeMap: mutable.Map[String, Value] = mutable.Map((name, Value.Unknown(location)))
        val evalScope = Scope(Some(scope), scopeMap)

        // TODO: Make sure it is a compile-time error to directly use the reference in the expression
        val evalValue = evaluate(value, evalScope, mode)

        if (mode == InterpreterMode.Runtime && evalValue.isDynamic) {
          throw EvalError(s"Could not evaluate $let, probably due to the value referencing itself", location)
        }

        scopeMap.addOne(name, evalValue)

        val blockEvalMode = if (mode.shouldTryToPartiallyEvaluate && evalValue.isDynamic) {
          InterpreterMode.PartialEvaluation
        } else {
          mode
        }

        val evalBlock = evaluate(block, location, evalScope, blockEvalMode)

        if (evalBlock.values.length == 1 && evalBlock.values.head.isStatic) {
          evalBlock.values.head
        } else {
          Value.Operation(Operation.Let(name, evalValue, evalBlock), location)
        }

//        if (evalValue.isStatic && interpreterMode != InterpreterMode.PartialEvaluation) {
//
//        } else if (interpreterMode.shouldTryToPartiallyEvaluate) {
//          // TODO: If `evalValue` is an operation or a struct, try to partially evaluate again, now knowing that it
//          //       refers to itself. But be careful because it can now go infinitely recursive.
//          //       Maybe it's best to not try to partially evaluate anymore and leave it at that?
//
//          val evalBlock = evaluate(block, location, evalScope, InterpreterMode.PartialEvaluation)
//        }

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        if (mayBeVarCall) {
          scope.find(name) match {
            case Some(Value.Unknown(_)) => value
            case Some(lambda @ Value.Lambda(_, _)) =>
              callMethod(lambda, "call", arguments, scope, location, shouldEvalTarget = false, mode)
            case Some(value) =>
              callMethod(value, "call", arguments, scope, location, shouldEvalTarget = false, mode)
              // evalError(value, "Cannot call this object as a function")
            case None =>
              callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, mode)
          }
        } else {
          callMethod(target, name, arguments, scope, location, shouldEvalTarget = true, mode)
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
    mode: InterpreterMode
  ): Value = {
    var targetWasLambda = false
    val evalTarget = if (shouldEvalTarget) {
      target match {
        case Value.Operation(_, _) => evaluate(target, scope, mode)
        case Value.Lambda(Lambda(params, _, body, traits), l) =>
          targetWasLambda = true
          Value.Lambda(Lambda(params, Some(scope), body, traits), l)
        case _ => target
      }
    } else target

    val evaledPositionalArguments = arguments.positional.map(evaluate(_, scope, mode))
    val evaledNamedArguments = arguments.named.view.mapValues(evaluate(_, scope, mode))
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

      logger.debug(s"[$mode] Will evaluate call $call in $scope")

      val context = CallContext(this, mode)

      Core.nativeValueFor(evalTarget).method(context, name, location) match {
        case Some(method) =>
          if (canEvaluate(method.traits, mode)) {
            // TODO: Add self as a separate parameter?
            val result = method.call(context, Arguments(evalTarget +: evaledArguments.positional, evaledArguments.named), location)

            logger.debug(s"$call -> $result")

            result
          } else if (mode == InterpreterMode.Runtime && !method.traits.contains(LambdaTrait.Runtime)) {
            throw EvalError(s"Could not evaluate compile-time-only function call $call", location)
          } else {
            logger.debug(s"Not evaluating $call because it does not have the required trait for $mode")
            call
          }

        case None => throw EvalError(s"Cannot call method $name on ${evalTarget.toString}", location)
      }
    } else {
      val partiallyEvaluatedTarget = if (mode.shouldTryToPartiallyEvaluate && shouldEvalTarget && targetWasLambda) {
        evalTarget match {
          case Value.Lambda(_, _) => evaluate(evalTarget, scope, InterpreterMode.PartialEvaluation)
          case _ => evalTarget
        }
      } else target

      val call = Value.Operation(Operation.Call(
        name = name,
        target = partiallyEvaluatedTarget,
        arguments = evaledArguments,
        mayBeVarCall = false
      ), location)

      call
    }
  }

  private def evaluate(block: Operation.Block, location: Option[Location], scope: Scope, mode: InterpreterMode): Operation.Block = {
    val evalValue = evaluate(Value.Operation(block, location), scope, mode)

    evalValue match {
      case Value.Operation(evalBlock @ Operation.Block(_), _) => evalBlock
      case _ => Operation.Block(Seq(evalValue))
    }
  }

  private def canEvaluate(traits: Set[LambdaTrait], mode: InterpreterMode): Boolean = {
    mode match {
      case InterpreterMode.PartialEvaluation => traits.contains(LambdaTrait.Partial)
      case InterpreterMode.CompileTime => traits.contains(LambdaTrait.CompileTime)
      case InterpreterMode.Runtime => traits.contains(LambdaTrait.Runtime)

      // TODO: Should this switch to Partial if the function supports that instead?
      case InterpreterMode.CompileTimeOrPartial => traits.contains(LambdaTrait.CompileTime)
    }
  }

  private def evalError(value: Value, message: String): Nothing = {
    throw EvalError(s"Cannot evaluate ${value.inspectAST}. $message".strip(), value.location)
  }
}
