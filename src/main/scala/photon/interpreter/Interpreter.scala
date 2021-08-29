package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.{Arguments, BoundFunction, FunctionTrait, Location, Operation, PhotonError, RealValue, Scope, Value, Variable}
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

  def evaluate(astValue: ASTValue): Value = evaluate(
    ASTToValue.transform(astValue, core.staticRootScope),
    core.rootScope
  )

  def evaluate(astBlock: ASTBlock): Value = {
    val block = ASTToValue.transformBlock(astBlock, core.staticRootScope)

    evaluate(
      Value.Operation(block, None),
      core.rootScope
    )
  }

  def evaluate(value: Value, scope: Scope): Value = {
    val result = evaluateCompileTime(value, scope)

    result.realValueOrSelf
  }

  private def evaluateCompileTime(value: Value, scope: Scope): Value = {
    value match {
      case Value.Real(_, _) => value
      case Value.Operation(operation, location) => evaluateOperation(operation, scope, location)
    }
  }

  private def evaluateOperation(operation: Operation, scope: Scope, location: Option[Location]): Value = {
    operation match {
      case Operation.Block(values, _) =>
        val lastValueIndex = values.length - 1
        val evaledValues = values.map(evaluateCompileTime(_, scope))
          .view
          .zipWithIndex
          .filter { case (value, index) => index == lastValueIndex || value.isOperation }
          .map(_._1)
          .toSeq

        val realValue =
          if (evaledValues.isEmpty) Some(RealValue.Nothing)
          else if (evaledValues.length == 1) evaledValues.last.realValue
          else None

        Value.Operation(
          Operation.Block(evaledValues, realValue),
          location
        )

      case Operation.Let(name, letValue, block, _) =>
        // TODO: Make this not nothing and maybe remove the need for the `Variable` class
        val variable = new Variable(name, Value.Real(RealValue.Nothing, location))
        val letScope = scope.newChild(Seq(variable))

        val letResult = evaluateCompileTime(letValue, letScope)

        variable.dangerouslySetValue(letResult)

        val blockResult = evaluateCompileTime(Value.Operation(block, location), letScope)

        val letUsesVariable = letResult.unboundNames.contains(variable.name)
        val blockUsesVariable = blockResult.unboundNames.contains(variable.name)

        val realValue =
          // The idea here is to check if `letResult` has fully evaluated. If it still has unevaluated
          // operations, then we don't want to set the real value, as it may skip evaluating these
          if (letResult.realValueAsValue.isDefined) blockResult.realValue
          else None

        if (!letUsesVariable && !blockUsesVariable) {
          // TODO: Smarter check with traits?
          if (letResult.isOperation) {
            // We should keep the operation as it may have side-effects
            return Value.Operation(
              Operation.Block(Seq(letResult, blockResult), realValue),
              location
            )
          } else {
            // We can remove the value - it shouldn't have any side-effects
            return blockResult
          }
        }

        Value.Operation(
          Operation.Let(name, letResult, blockResult.asBlock, realValue),
          location
        )

      case Operation.Reference(name, _) =>
        val realValue = scope.find(name) match {
          case Some(variable) => variable.value.realValue
          case None => throw EvalError(s"Cannot find name ${name.originalName} in scope $scope", location)
        }

        Value.Operation(
          Operation.Reference(name, realValue),
          location
        )

      case Operation.Function(fn, _) =>
        val boundFn = BoundFunction(
          fn,
          scope,
          traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)
        )
        // TODO: Maybe remove the indirection?
        val realValue = RealValue.BoundFn(boundFn)

        Value.Operation(
          Operation.Function(fn, Some(realValue)),
          location
        )

      case Operation.Call(target, name, arguments, _) =>
        val targetResult = evaluateCompileTime(target, scope)
        val argumentResults = arguments.map(evaluateCompileTime(_, scope))

        val realTargetMaybe = targetResult.realValue
        val realPositionalArguments = argumentResults.positional.map(_.realValueAsValue)
        val realNamedArguments = argumentResults.named.view.mapValues(_.realValueAsValue).toMap

        val realTarget = realTargetMaybe match {
          case Some(realValue) => realValue
          case None => return Value.Operation(
            Operation.Call(targetResult, name, argumentResults, None),
            None
          )
        }

        val targetIsFullyKnown = realTarget.isFullyKnown

        if (targetIsFullyKnown) {
          val argumentsAreAllReal =
            realPositionalArguments.forall(_.isDefined) && realNamedArguments.forall(_._2.isDefined)

          if (argumentsAreAllReal) {
            val realArguments = Arguments(
              positional = realPositionalArguments.map(_.get),
              named = realNamedArguments.view.mapValues(_.get).toMap
            )
            val argumentsAreFullyKnown = realArguments.forall(_.isFullyKnown)

            val nativeValueForTarget = Core.nativeValueFor(realTarget, location)
            val method = nativeValueForTarget.method(name, location) match {
              case Some(value) => value
              case None => throw EvalError(s"Cannot call method $name on $realTarget", location)
            }

            val hasCompileTimeRunMode = method.traits.contains(FunctionTrait.CompileTime)

            val canCallCompileTime = hasCompileTimeRunMode && argumentsAreFullyKnown

            if (canCallCompileTime) {
              // TODO: Correct these
              val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)

              val result = method.call(
                callContext,
                realArguments.withSelf(realTarget.asValue(targetResult.location)),
                location
              )

              logger.debug(s"[compile-time] [call] Evaluated $operation to $result")

              result
            } else {
              Value.Operation(operation, location)
            }
          } else {
            // TODO: Partial evaluation
            Value.Operation(operation, location)
          }
        } else {
          Value.Operation(operation, location)
        }
    }
  }
}
