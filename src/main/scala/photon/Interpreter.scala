package photon

import com.typesafe.scalalogging.Logger
import photon.Operation.Block
import photon.core.{CallContext, Core}
import photon.transforms.AssignmentTransform

sealed abstract class RunMode(val name: String) {}

object RunMode {
  case object Runtime extends RunMode("runtime")
  case object CompileTime extends RunMode("compile-time")
}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

case class CompileTimeResult(codeValue: Value, realValue: Value)

class Interpreter(val runMode: RunMode) {
  private val logger = Logger[Interpreter]
  private val core = new Core

  def macroHandler(name: String, parser: Parser): Option[Value] =
    core.macroHandler(CallContext(this, runMode), name, parser)

  def evaluate(value: Value): Value =
    evaluate(value, core.rootScope, runMode)

  def evaluate(value: Value, scope: Scope, runMode: RunMode): Value =
    runMode match {
      case RunMode.Runtime => evaluateRuntime(value, scope)
      case RunMode.CompileTime =>
        val result = evaluateCompileTime(value, scope, inPartialContext = false)

        if (result.realValue.isStatic) {
          result.realValue
        } else {
          result.codeValue
        }
    }

  private def evaluateCompileTime(value: Value, scope: Scope, inPartialContext: Boolean): CompileTimeResult = {
    value match {
      case Value.Unknown(_) => ???
      case Value.Nothing(_) => CompileTimeResult(value, value)
      case Value.Boolean(_, _) => CompileTimeResult(value, value)
      case Value.Int(_, _, _) => CompileTimeResult(value, value)
      case Value.Float(_, _) => CompileTimeResult(value, value)
      case Value.String(_, _) => CompileTimeResult(value, value)
      case Value.Native(_, _) => CompileTimeResult(value, value)
      case Value.Struct(_, _) => CompileTimeResult(value, value)

      case Value.Lambda(Lambda(params, _, body, traits), location) =>
        val evalScope = Scope(
          Some(scope),
          params.map { param => (param.name, Value.Unknown(location)) }.toMap
        )

        val CompileTimeResult(codeEvalBody, realEvalBody) =
          evaluateCompileTime(Value.Operation(body, location), evalScope, inPartialContext = true)

        val codeValue = Value.Lambda(Lambda(params, Some(scope), asBlock(codeEvalBody), traits), location)
        val realValue = Value.Lambda(Lambda(params, Some(scope), asBlock(realEvalBody), traits), location)

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.Block(values), location) =>
        val lastIndex = values.size - 1
        val codeValuesBuilder = Seq.newBuilder[Value]
        val realValuesBuilder = Seq.newBuilder[Value]

        codeValuesBuilder.sizeHint(values.size)
        realValuesBuilder.sizeHint(values.size)

        values.zipWithIndex.foreach { case (value, index) =>
          val CompileTimeResult(codeValue, realValue) = evaluateCompileTime(value, scope, inPartialContext)

          if (!realValue.isNothing || index == lastIndex) {
            codeValuesBuilder.addOne(codeValue)
          }

          if (realValue.isOperation || index == lastIndex) {
            realValuesBuilder.addOne(realValue)
          }
        }

        val codeValues = codeValuesBuilder.result()
        val realValues = realValuesBuilder.result()

        val codeValue = if (codeValues.length == 1) {
          codeValues.last
        } else {
          Value.Operation(Operation.Block(codeValues), location)
        }
        val realValue = if (realValues.length == 1) {
          realValues.last
        } else {
          Value.Operation(Operation.Block(realValues), location)
        }

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.Let(name, letValue, block), location) =>
        val letScopeMap = collection.mutable.Map[String, Value]((name, Value.Unknown(location)))
        val letScope = Scope(Some(scope), letScopeMap)

        val CompileTimeResult(codeLetValue, realLetValue) = evaluateCompileTime(letValue, letScope, inPartialContext)

        letScopeMap.addOne((name, realLetValue))

        val CompileTimeResult(codeBlockValue, realBlockValue) =
          evaluateCompileTime(
            Value.Operation(block, location),
            letScope,
            inPartialContext = !realLetValue.isStatic
          )

        val codeValue = Value.Operation(Operation.Let(name, codeLetValue, asBlock(codeBlockValue)), location)
        val realValue = if (realLetValue.isOperation || realBlockValue.isOperation) {
          Value.Operation(Operation.Let(name, realLetValue, asBlock(realBlockValue)), location)
        } else {
          realBlockValue
        }

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.NameReference(name), location) =>
        val codeValue = value

        val foundValue = scope.find(name)
        val realValue = foundValue match {
          case Some(value) => value
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val positionalEvals =
          arguments.positional.map(evaluateCompileTime(_, scope, inPartialContext))
        val namedEvals =
          arguments.named.view.mapValues(evaluateCompileTime(_, scope, inPartialContext)).toMap

        val codeEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(codeValue, _) => codeValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, _) => codeValue }.toMap
        )
        val realEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(_, realValue) => realValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(_, realValue) => realValue }.toMap
        )

        val (CompileTimeResult(codeEvalTarget, realEvalTarget), isVarCall) = if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) => (CompileTimeResult(value, value), true)
            case None => (evaluateCompileTime(target, scope, inPartialContext), false)
          }
        } else {
          (evaluateCompileTime(target, scope, inPartialContext), false)
        }

        val codeValue = if (mayBeVarCall) {
          Value.Operation(
            Operation.Call(target, name, codeEvalArguments, mayBeVarCall),
            location
          )
        } else {
          Value.Operation(
            Operation.Call(codeEvalTarget, name, codeEvalArguments, mayBeVarCall),
            location
          )
        }

        val canCallFunction =
          realEvalTarget.isStatic &&
            realEvalArguments.positional.forall(_.isStatic) &&
            realEvalArguments.named.view.values.forall(_.isStatic)

        val resultValue = if (canCallFunction) {
          val nativeValueForTarget = Core.nativeValueFor(realEvalTarget)

          val callContext = CallContext(this, runMode)

          val method = nativeValueForTarget.method(
            callContext,
            if (isVarCall) { "call" } else { name },
            location
          ) match {
            case Some(value) => value
            case None => throw EvalError(s"Cannot call method $name on $realEvalTarget", location)
          }

          val hasCompileTimeRunMode = method.traits.contains(LambdaTrait.CompileTime)
          val canEvaluateBasedOnPurity = !inPartialContext || method.traits.contains(LambdaTrait.Pure)

          if (hasCompileTimeRunMode && canEvaluateBasedOnPurity) {
            method.call(
              callContext,
              addSelfArgument(realEvalArguments, realEvalTarget),
              location
            )
          } else { codeValue }
        } else { codeValue }

        CompileTimeResult(resultValue, resultValue)
    }
  }

  private def evaluateRuntime(value: Value, scope: Scope): Value = {
    value match {
      case Value.Unknown(_) => ???
      case Value.Nothing(_) => value
      case Value.Boolean(_, _) => value
      case Value.Int(_, _, _) => value
      case Value.Float(_, _) => value
      case Value.String(_, _) => value
      case Value.Native(_, _) => value
      case Value.Struct(_, _) => value
      case Value.Lambda(Lambda(params, _, body, traits), location) =>
        Value.Lambda(Lambda(params, Some(scope), body, traits), location)

      case Value.Operation(Operation.Block(values), location) =>
        val evaluatedValues = values.map(evaluate(_, scope, runMode))

        if (evaluatedValues.nonEmpty) {
          evaluatedValues.last
        } else {
          Value.Nothing(location)
        }

      case Value.Operation(Operation.Let(name, letValue, block), location) =>
        val letScopeMap = collection.mutable.Map[String, Value]((name, Value.Unknown(location)))
        val letScope = Scope(Some(scope), letScopeMap)

        val evaluatedLetValue = evaluate(letValue, letScope, runMode)

        letScopeMap.addOne((name, evaluatedLetValue))

        evaluate(Value.Operation(block, location), letScope, runMode)

      case Value.Operation(Operation.NameReference(name), location) =>
        val foundValue = scope.find(name)

        foundValue match {
          case Some(value) => value
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val evaluatedArguments = Arguments(
          positional = arguments.positional.map(evaluate(_, scope, runMode)),
          named = arguments.named.view.mapValues(evaluate(_, scope, runMode)).toMap
        )

        val (evaluatedTarget, isVarCall) = if (mayBeVarCall) {
          scope.find(name) match {
            case Some(value) => (value, true)
            case None => (evaluate(target, scope, runMode), false)
          }
        } else {
          (evaluate(target, scope, runMode), false)
        }

        Core.nativeValueFor(evaluatedTarget).callOrThrowError(
          CallContext(this, runMode),
          if (isVarCall) { "call" } else { name },
          addSelfArgument(evaluatedArguments, evaluatedTarget),
          location
        )
    }
  }

  private def addSelfArgument(arguments: Arguments, self: Value) =
    Arguments(self +: arguments.positional, arguments.named)

  private def asBlock(value: Value): Block = {
    value match {
      case Value.Operation(block @ Operation.Block(_), _) => block
      case _ => Operation.Block(Seq(value))
    }
  }
}
