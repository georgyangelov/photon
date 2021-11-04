package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.New.TypeObject
import photon.{AnyType, ArgumentType, Arguments, BoundValue, FunctionTrait, Location, New, Operation, PhotonError, PureValue, RealValue, Scope, UnboundValue, Value, Variable}
import photon.core.{Core, FunctionType, NothingType}
import photon.frontend.{ASTBlock, ASTToValue, ASTValue, Parser}
import photon.core.Conversions._

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

sealed abstract class RunMode(val name: String) {}
object RunMode {
  case object Runtime extends RunMode("runtime")
  case object CompileTime extends RunMode("compile-time")
  case object ParseTime extends RunMode("parse-time")
}

case class CallStackEntry()

case class CallContext(
  interpreter: Interpreter,
  runMode: RunMode,
  callStack: Seq[CallStackEntry],
  callScope: Scope
) {
  def callOrThrowError(target: RealValue, name: String, args: Arguments[RealValue], location: Option[Location]) = {
    val typeObject = target.typeObject.getOrElse {
      throw EvalError(s"Could not call $target as it does not have a type", location)
    }

    typeObject.instanceMethod(name).getOrElse {
      throw EvalError(s"There is no method $name on $typeObject", location)
    }.call(this, Arguments(None, Seq.empty, Map.empty), location)
  }
}

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

  def evaluate(value: UnboundValue, scope: Scope): Value =
    evaluateCompileTime(value, scope)

  private def evaluateCompileTime(value: UnboundValue, scope: Scope): UnboundValue = value match {
    case value: PureValue => value
    case operation: Operation => evaluateOperation(operation, scope)
  }

  private def evaluateOperation(operation: Operation, scope: Scope): UnboundValue = {
    operation match {
      case Operation.Block(values, _, _, location) =>
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

        val typeObject = if (evaledValues.nonEmpty) evaledValues.last.typeObject else Some(NothingType)

        Operation.Block(evaledValues, typeObject, realValue, location)

      case Operation.Let(name, letValue, block, _, _, location) =>
        val variable = new Variable(name, None)
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
            return Operation.Block(Seq(letResult, blockResult), blockResult.typeObject, realValue, location)
          } else {
            // We can remove the value - it shouldn't have any side-effects
            return blockResult
          }
        }

        Operation.Let(name, letResult, blockResult.asBlock, blockResult.typeObject, realValue, location)

      case Operation.Reference(name, _, _, location) =>
        val value = scope.find(name) match {
          case Some(Variable(_, Some(value))) => value
          case Some(Variable(_, None)) => throw EvalError(s"Cannot use the name ${name.originalName} during declaration", location)
          case _ => throw EvalError(s"Cannot find name ${name.originalName} in scope $scope", location)
        }

        Operation.Reference(name, value.typeObject, value.realValue, location)

      case Operation.Function(fn, _, _, location) =>
        val traits: Set[FunctionTrait] = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)

        // TODO: Infer return type
        val typeObject = if (fn.params.forall(_.typeValue.isDefined) && fn.returnType.isDefined) {
          // TODO: Parameters should be able to reference previous ones
          val argTypes = fn.params
            .map(_.typeValue.get)
            .map(evaluateCompileTime(_, scope))
            .map(_.realValue.getOrElse {
              throw EvalError(
                "Types of function params must be fully known at compile-time",
                location
              )
            })
            .map(_.asNative[New.TypeObject])

          // TODO: This should be able to reference parameters
          val returnType = evaluateCompileTime(fn.returnType.get, scope).realValue.getOrElse {
            throw EvalError(
              "Return type of function must be fully known at compile-time",
              location
            )
          }.asNative[New.TypeObject]

          Some(FunctionType(
            // TODO
            Set(FunctionTrait.CompileTime, FunctionTrait.Runtime),
            fn.params.zip(argTypes).map { case (parameter, typeObject) =>
              ArgumentType(parameter.name.originalName, typeObject)
            },
            returnType
          ))
        } else None

        val realValue = BoundValue.Function(fn, traits, scope, typeObject, location)

        Operation.Function(fn, typeObject, Some(realValue), location)

      case Operation.Call(target, name, arguments, _, _, location) =>
        val targetResult = evaluateCompileTime(target, scope)
        val argumentResults = arguments.map(evaluateCompileTime(_, scope))

        val targetType = targetResult.typeObject.getOrElse {
          throw EvalError(s"Method target ($targetResult) must have a type", location)
        }

//        val methodType = targetType.methodTypes.find(_.name == name).getOrElse {
//          throw EvalError(s"Method $name does not have a type on $targetResult's type", location)
//        }

        val method = targetType.instanceMethod(name).getOrElse {
          throw EvalError(
            s"Method $name was present in the types but is not actually present in the instance methods",
            location
          )
        }

        val methodType = method.methodType(
          argumentResults.map(_.typeObject.get)
        )

        // TODO: Typecheck arguments

        val realPositionalArguments = argumentResults.positional.map(_.realValue)
        val realNamedArguments = argumentResults.named.view.mapValues(_.realValue).toMap

        val targetIsFullyKnown = targetResult.realValue.forall(_.isFullyKnown)
        val argumentsAreFullyKnown =
          realPositionalArguments.forall(_.exists(_.isFullyKnown)) &&
            realNamedArguments.forall(_._2.exists(_.isFullyKnown))

        if (method.traits.contains(FunctionTrait.CompileTime) && targetIsFullyKnown && argumentsAreFullyKnown) {
          // TODO: Correct these
          val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)
          val arguments = Arguments[RealValue](
            self = targetResult.realValue,
            positional = realPositionalArguments.map(_.get),
            named = realNamedArguments.view.mapValues(_.get).toMap
          )

          val result = method.call(callContext, arguments, location)
          val resultWithCorrectType =
            // TODO: This is an edge case I'm not sure should be here.
            //       It's currently done to support the dynamic type of Class.new
            if (!result.typeObject.contains(AnyType)) {
              result
            } else {
              changeType(result, Some(methodType.returns))
            }

          logger.debug(s"[compile-time] [call] Evaluated $operation to $resultWithCorrectType")

          return core.unbinder.unbind(resultWithCorrectType, scope)
        }

        if (method.traits.contains(FunctionTrait.Partial)) {
          // TODO: Correct these
          val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)

          // TODO: Use real values if possible
          val arguments = argumentResults
            .asInstanceOf[Arguments[Value]]
            .withSelf(targetResult.realValue.getOrElse(targetResult))

          val result = method.partialCall(callContext, arguments, location)
          val resultWithCorrectType = changeType(result, Some(methodType.returns))

          logger.debug(s"[partial] [call] Evaluated $operation to $resultWithCorrectType")

          return core.unbinder.unbind(resultWithCorrectType, scope)
        }

        Operation.Call(
          targetResult,
          name,
          argumentResults,
          Some(methodType.returns),
          None,
          location
        )













//        val realTarget = realTargetMaybe match {
//          case Some(realValue) => realValue
//          case None => return Operation.Call(
//            targetResult,
//            name,
//            argumentResults,
//            Some(methodType.returnType),
//            None,
//            location
//          )
//        }
//
//        val targetIsFullyKnown = realTarget.isFullyKnown
//
//        val nativeValueForTarget = Core.nativeValueFor(realTarget)
//        val methodMaybe = nativeValueForTarget.method(name, location)
//
//        if (targetIsFullyKnown) {
//          val method = methodMaybe match {
//            case Some(value) => value
//            case None => throw EvalError(s"Cannot call method $name on $realTarget", location)
//          }
//
//          val argumentsAreAllReal =
//            realPositionalArguments.forall(_.isDefined) && realNamedArguments.forall(_._2.isDefined)
//
//          if (argumentsAreAllReal) {
//            val realArguments = Arguments(
//              if (realTarget.callsShouldIncludeSelf)
//                Some(realTarget)
//              else
//                None,
//              positional = realPositionalArguments.map(_.get),
//              named = realNamedArguments.view.mapValues(_.get).toMap
//            )
//            val argumentsAreFullyKnown = realArguments.forall(_.isFullyKnown)
//
//            val hasCompileTimeRunMode = method.traits.contains(FunctionTrait.CompileTime)
//
//            val canCallCompileTime = hasCompileTimeRunMode && argumentsAreFullyKnown
//
//            if (canCallCompileTime) {
//              // TODO: Correct these
//              val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)
//
//              val result = method.call(callContext, realArguments, location)
//
//              logger.debug(s"[compile-time] [call] Evaluated $operation to $result")
//
//              return core.unbinder.unbind(result, scope)
//            }
//          }
//        }
//
//        if (methodMaybe.exists(_.traits.contains(FunctionTrait.Partial))) {
//          val callContext = CallContext(this, RunMode.CompileTime, callStack = Seq.empty, callScope = scope)
//
//          val result = methodMaybe.get.partialCall(
//            callContext,
//            argumentResults.asInstanceOf[Arguments[Value]].withSelf(realTarget),
//            location
//          )
//
//          logger.debug(s"[partial] [call] Evaluated $operation to $result")
//
//          return core.unbinder.unbind(result, scope)
//        }
//
//        Operation.Call(targetResult, name, argumentResults, , None, location)
    }
  }

  private def changeType(value: Value, typeObject: Option[TypeObject]): Value = value match {
    case pure: PureValue => pure

    case BoundValue.Function(fn, traits, scope, _, location) =>
      BoundValue.Function(fn, traits, scope, typeObject, location)

    case BoundValue.Object(values, scope, _, location) =>
      BoundValue.Object(values, scope, typeObject, location)

    case Operation.Block(values, _, realValue, location) =>
      Operation.Block(values, typeObject, realValue, location)

    case Operation.Let(name, letValue, block, _, realValue, location) =>
      Operation.Let(name, letValue, block, typeObject, realValue, location)

    case Operation.Reference(name, _, realValue, location) =>
      Operation.Reference(name, typeObject, realValue, location)

    case Operation.Function(fn, _, realValue, location) =>
      Operation.Function(fn, typeObject, realValue, location)

    case Operation.Call(target, name, arguments, _, realValue, location) =>
      Operation.Call(target, name, arguments, typeObject, realValue, location)
  }
}
