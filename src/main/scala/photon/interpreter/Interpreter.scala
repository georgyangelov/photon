package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.{Arguments, BoundValue, FunctionTrait, Location, Operation, PhotonError, PureValue, Scope, UnboundValue, Value, Variable}
import photon.core.{CallContext, Core}
import photon.frontend.{ASTBlock, ASTToValue, ASTValue, Parser}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

sealed abstract class RunMode(val name: String) {}
object RunMode {
  case object Runtime extends RunMode("runtime")
  case object CompileTime extends RunMode("compile-time")
  case object ParseTime extends RunMode("parse-time")
}

case class CallStackEntry()

class Interpreter {
  private val logger = Logger[Interpreter]
  private val core = new Core

  def macroHandler(name: String, parser: Parser): Option[ASTValue] =
    core.macroHandler(
      CallContext(this, RunMode.ParseTime, callStack = Seq.empty, callScope = core.rootScope),
      name,
      parser
    )

  def evaluate(astValue: ASTValue): UnboundValue = {
    val value = ASTToValue.transform(astValue, core.staticRootScope)
    val result = evaluate(value, core.rootScope)

    core.unbinder.unbind(result.realValue.getOrElse(result), core.rootScope)
  }

  def evaluate(astBlock: ASTBlock): UnboundValue = {
    // TODO: Pass actual location
    val block = ASTToValue.transformBlock(astBlock, core.staticRootScope, None)
    val result = evaluate(block, core.rootScope)

    core.unbinder.unbind(result.realValue.getOrElse(result), core.rootScope)
  }

  def evaluate(value: UnboundValue, scope: Scope): Value = {
    val result = evaluateCompileTime(value, scope)

    // TODO: Unbind result?
    // result.realValueOrSelf
    result
  }

  private def evaluateCompileTime(value: UnboundValue, scope: Scope): UnboundValue = value match {
    case value: PureValue => value
    case operation: Operation => evaluateOperation(operation, scope)
  }

  private def evaluateOperation(operation: Operation, scope: Scope): UnboundValue = {
    operation match {
      case Operation.Block(values, _, location) =>
        val lastValueIndex = values.length - 1
        val evaledValues = values.map(evaluateCompileTime(_, scope))
          .view
          .zipWithIndex
          .filter { case (value, index) => index == lastValueIndex || value.mayHaveSideEffects }
          .map(_._1)
          .toSeq

        val realValues = evaledValues.flatMap(_.realValue)
        val allValuesCanBeEvaluated = realValues.length == evaledValues.length

        val realValue =
          if (evaledValues.isEmpty) Some(PureValue.Nothing(location))
          else if (allValuesCanBeEvaluated) Some(realValues.last)
          else None

        Operation.Block(evaledValues, realValue, location)

      case Operation.Let(name, letValue, block, _, location) =>
        // TODO: Make this not nothing and maybe remove the need for the `Variable` class
        val variable = new Variable(name, PureValue.Nothing(location))
        val letScope = scope.newChild(Seq(variable))

        val letResult = evaluateCompileTime(letValue, letScope)

        variable.dangerouslySetValue(letResult)

        val blockResult = evaluateCompileTime(block, letScope)

        val letUsesVariable = letResult.unboundNames.contains(variable.name)
        val blockUsesVariable = blockResult.unboundNames.contains(variable.name)

        val realValue =
          // The idea here is to check if `letResult` has fully evaluated. If it still has unevaluated
          // operations, then we don't want to set the real value, as it may skip evaluating these
          if (letResult.realValue.isDefined) blockResult.realValue
          else None

        if (!letUsesVariable && !blockUsesVariable) {
          // TODO: Smarter check with traits?
          if (letResult.mayHaveSideEffects) {
            // We should keep the operation as it may have side-effects
            return Operation.Block(Seq(letResult, blockResult), realValue, location)
          } else {
            // We can remove the value - it shouldn't have any side-effects
            return blockResult
          }
        }

        Operation.Let(name, letResult, blockResult.asBlock, realValue, location)

      case Operation.Reference(name, _, location) =>
        val realValue = scope.find(name) match {
          case Some(variable) => variable.value.realValue
          case None => throw EvalError(s"Cannot find name ${name.originalName} in scope $scope", location)
        }

        Operation.Reference(name, realValue, location)

      case Operation.Function(fn, _, location) =>
        val traits: Set[FunctionTrait] = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)
        val realValue = BoundValue.Function(fn, traits, scope, location)

        Operation.Function(fn, Some(realValue), location)

      case Operation.Call(target, name, arguments, _, location) =>
        val targetResult = evaluateCompileTime(target, scope)
        val argumentResults = arguments.map(evaluateCompileTime(_, scope))

        val realTargetMaybe = targetResult.realValue
        val realPositionalArguments = argumentResults.positional.map(_.realValue)
        val realNamedArguments = argumentResults.named.view.mapValues(_.realValue).toMap

        val realTarget = realTargetMaybe match {
          case Some(realValue) => realValue
          case None => return Operation.Call(targetResult, name, argumentResults, None, location)
        }

        val targetIsFullyKnown = realTarget.isFullyKnown

        val nativeValueForTarget = Core.nativeValueFor(realTarget)
        val methodMaybe = nativeValueForTarget.method(name, location)

        if (targetIsFullyKnown) {
          val method = methodMaybe match {
            case Some(value) => value
            case None => throw EvalError(s"Cannot call method $name on $realTarget", location)
          }

          val argumentsAreAllReal =
            realPositionalArguments.forall(_.isDefined) && realNamedArguments.forall(_._2.isDefined)

          if (argumentsAreAllReal) {
            val realArguments = Arguments(
              positional = realPositionalArguments.map(_.get),
              named = realNamedArguments.view.mapValues(_.get).toMap
            )
            val argumentsAreFullyKnown = realArguments.forall(_.isFullyKnown)

            val hasCompileTimeRunMode = method.traits.contains(FunctionTrait.CompileTime)

            val canCallCompileTime = hasCompileTimeRunMode && argumentsAreFullyKnown

            if (canCallCompileTime) {
              // TODO: Correct these
              val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)

              val result = method.call(
                callContext,
                realArguments.withSelf(realTarget),
                location
              )

              logger.debug(s"[compile-time] [call] Evaluated $operation to $result")

              return core.unbinder.unbind(result, scope)
            }
          }
        }

        if (methodMaybe.exists(_.traits.contains(FunctionTrait.Partial))) {
          val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)

          val result = methodMaybe.get.partialCall(
            callContext,
            argumentResults.asInstanceOf[Arguments[Value]].withSelf(realTarget),
            location
          )

          logger.debug(s"[partial] [call] Evaluated $operation to $result")

          return core.unbinder.unbind(result, scope)
        }

        Operation.Call(targetResult, name, argumentResults, None, location)
    }
  }
}
