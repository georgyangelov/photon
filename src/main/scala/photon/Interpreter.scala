package photon

import com.typesafe.scalalogging.Logger
import photon.Operation.Block
import photon.core.{CallContext, Core}

import scala.annotation.tailrec

sealed abstract class RunMode(val name: String) {}

object RunMode {
  case object Runtime extends RunMode("runtime")
  case object CompileTime extends RunMode("compile-time")
  case object ParseTime extends RunMode("parse-time")
}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}


case class CompileTimeInspection(nameUses: Set[Variable]) {
  def combineWith(other: CompileTimeInspection) =
    CompileTimeInspection(nameUses ++ other.nameUses)

  def withoutVariables(variables: Seq[Variable]) =
    CompileTimeInspection(nameUses -- variables)
}

object CompileTimeInspection {
  val empty = CompileTimeInspection(Set.empty)
}

case class CompileTimeContext(
  partialEvaluation: Boolean,

  renameVariables: Boolean,

  // TODO: Integrate in `Variable`?
  currentRenames: Map[Variable, String]
)

case class CompileTimeResult(
  codeValue: Value,
  realValue: Value,
  inspection: CompileTimeInspection
)

class Interpreter(val runMode: RunMode) {
  private val logger = Logger[Interpreter]
  private val core = new Core

  def macroHandler(name: String, parser: Parser): Option[Value] =
    core.macroHandler(CallContext(this, RunMode.ParseTime), name, parser)

  def evaluate(value: Value): Value =
    evaluate(value, core.rootScope, runMode)

  def evaluate(value: Value, scope: Scope, runMode: RunMode): Value =
    runMode match {
      case RunMode.Runtime => evaluateRuntime(value, scope)
      case RunMode.CompileTime | RunMode.ParseTime =>
        val result = evaluateCompileTime(
          value,
          scope,
          CompileTimeContext(
            partialEvaluation = false,
            renameVariables = runMode == RunMode.ParseTime,
            currentRenames = Map.empty
          )
        )

        if (isFullyEvaluated(result.realValue, runMode)) {
          result.realValue
        } else {
          result.codeValue
        }
    }

  private def evaluateCompileTime(
    value: Value,
    scope: Scope,
    context: CompileTimeContext
  ): CompileTimeResult = {
    value match {
      case Value.Unknown(_) => ???
      case Value.Nothing(_) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.Boolean(_, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.Int(_, _, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.Float(_, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.String(_, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.Native(_, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)
      case Value.Struct(_, _) => CompileTimeResult(value, value, CompileTimeInspection.empty)

      case Value.Lambda(Lambda(params, body, info), location) =>
        val evalScopeVariables = params.map { param =>
          new Variable(param.name, Value.Unknown(location))
        }
        val evalScope = scope.newChild(evalScopeVariables)

        val (renamedParams, evalRenames) = if (context.renameVariables) {
          val renamedParams = params.zip(evalScopeVariables)
            .map { case (param, variable) => Parameter(uniqueVariableName(variable), param.typeValue) }

          val evalRenames = context.currentRenames ++ evalScopeVariables.map { variable => (variable, uniqueVariableName(variable)) }.toMap

          (renamedParams, evalRenames)
        } else {
          (params, context.currentRenames)
        }

        val CompileTimeResult(codeEvalBody, realEvalBody, inspection) =
          evaluateCompileTime(
            Value.Operation(body, location),
            evalScope,
            CompileTimeContext(
              partialEvaluation = true,
              renameVariables = context.renameVariables,
              evalRenames
            )
          )

        val resultInspection = inspection.withoutVariables(evalScopeVariables)

        val lambdaInfo = LambdaInfo(
          scope,
          scopeVariables = resultInspection.nameUses,
          traits = info.traits
        )

        val codeValue = Value.Lambda(Lambda(renamedParams, asBlock(codeEvalBody), lambdaInfo), location)
        val realValue = if (isFullyEvaluated(realEvalBody, runMode)) {
          Value.Lambda(Lambda(renamedParams, asBlock(realEvalBody), lambdaInfo), location)
        } else {
          codeValue
        }

        logger.debug(s"[compile-time] [lambda] Evaluated $value to $realValue")

        CompileTimeResult(codeValue, realValue, resultInspection)

      case Value.Operation(Operation.LambdaDefinition(params, body), location) =>
        val lambdaInfo = LambdaInfo(
          scope,
          // Assuming this will be set by the call to evaluate for the lambda value
          scopeVariables = Set.empty,
          traits = Set(LambdaTrait.Partial, LambdaTrait.CompileTime, LambdaTrait.Runtime, LambdaTrait.Pure)
        )

        val lambda = Lambda(params, body, lambdaInfo)

        evaluateCompileTime(
          Value.Lambda(lambda, location),
          scope,
          context
        )

      case Value.Operation(Operation.Block(values), location) =>
        val lastIndex = values.size - 1
        val codeValuesBuilder = Seq.newBuilder[Value]
        val realValuesBuilder = Seq.newBuilder[Value]
        var resultInspection = CompileTimeInspection(Set.empty)

        codeValuesBuilder.sizeHint(values.size)
        realValuesBuilder.sizeHint(values.size)

        values.zipWithIndex.foreach { case (value, index) =>
          val CompileTimeResult(codeValue, realValue, inspection) =
            evaluateCompileTime(value, scope, context)

          resultInspection = resultInspection.combineWith(inspection)

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

        CompileTimeResult(codeValue, realValue, resultInspection)

      case Value.Operation(Operation.Let(name, letValue, block), location) =>
        val variable = new Variable(name, Value.Unknown(location))
        val letScope = scope.newChild(Seq(variable))

        val (newName, evalRenames) = if (context.renameVariables) {
          val newName = uniqueVariableName(variable)
          val evalRenames = context.currentRenames.updated(variable, newName)

          (newName, evalRenames)
        } else {
          (name, context.currentRenames)
        }

        // TODO: Make sure it is a compile-time error to directly use the reference in the expression
        val CompileTimeResult(codeLetValue, realLetValue, letInspection) =
          evaluateCompileTime(
            letValue,
            letScope,
            context.copy(currentRenames = evalRenames)
          )

        variable.dangerouslySetValue(realLetValue)

        val CompileTimeResult(codeBlockValue, realBlockValue, blockInspection) =
          evaluateCompileTime(
            Value.Operation(block, location),
            letScope,
            CompileTimeContext(
              partialEvaluation = !isFullyEvaluated(realLetValue, runMode),
              renameVariables = context.renameVariables,
              currentRenames = evalRenames
            )
          )

        val innerInspection = letInspection.combineWith(blockInspection)

        // The let name is unused, can be eliminated if it's known
        // If it's unknown it probably can't be, because the expression may have side effects
        if (!innerInspection.nameUses.contains(variable)) {
          val shouldEliminateLetValue = isFullyEvaluated(realLetValue, runMode)

          // TODO: !codeLetValue.isPure?
          val codeValue = if (shouldEliminateLetValue) {
            Value.Operation(asBlock(codeBlockValue), location)
          } else {
            val blockWithLetExpressionForSideEffects = Operation.Block(
              Seq(codeLetValue) ++ asBlock(codeBlockValue).values
            )

            Value.Operation(blockWithLetExpressionForSideEffects, location)
          }

          val realValue = if (isFullyEvaluated(realLetValue, runMode) && isFullyEvaluated(realBlockValue, runMode)) {
            realBlockValue
          } else {
            Value.Unknown(location)
          }

          if (isFullyEvaluated(realValue, runMode)) {
            logger.debug(s"[compile-time] [let] Evaluated $value to $realValue")
          } else {
            logger.debug(s"[compile-time] [let] Evaluated $value to $codeValue")
          }

          CompileTimeResult(codeValue, realValue, innerInspection.withoutVariables(Seq(variable)))
        } else {
          val codeValue = Value.Operation(Operation.Let(newName, codeLetValue, asBlock(codeBlockValue)), location)

          val realValue = if (isFullyEvaluated(realLetValue, runMode) && isFullyEvaluated(realBlockValue, runMode)) {
            realBlockValue
          } else {
            Value.Unknown(location)
          }

          if (isFullyEvaluated(realValue, runMode)) {
            logger.debug(s"[compile-time] [let] Evaluated $value to $realValue")
          } else {
            logger.debug(s"[compile-time] [let] Evaluated $value to $codeValue")
          }

          CompileTimeResult(codeValue, realValue, innerInspection.withoutVariables(Seq(variable)))
        }

      case Value.Operation(Operation.NameReference(name), location) =>
        val variable = scope.find(name) match {
          case Some(variable) => variable
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

        val newName = context.currentRenames.get(variable) match {
          case Some(newName) => newName
          case None => name
        }

        val codeValue = Value.Operation(Operation.NameReference(newName), location)
        val realValue = if (variable.value.isUnknown) { codeValue } else { variable.value }

        val inspection = CompileTimeInspection(Set(variable))

        CompileTimeResult(codeValue, realValue, inspection)

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val positionalEvals =
          arguments.positional.map(evaluateCompileTime(_, scope, context))
        val namedEvals =
          arguments.named.view.mapValues(evaluateCompileTime(_, scope, context)).toMap

        val codeEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(codeValue, _, _) => codeValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, _, _) => codeValue }.toMap
        )
        val realEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(_, realValue, _) => realValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(_, realValue, _) => realValue }.toMap
        )

        val argumentInspections = (positionalEvals ++ namedEvals.values)
          .map(_.inspection)
          .foldLeft(CompileTimeInspection.empty) { case (a, b) => a.combineWith(b) }

        val (CompileTimeResult(codeEvalTarget, realEvalTarget, targetInspection), isVarCall) = if (mayBeVarCall) {
          scope.find(name) match {
            case Some(variable) =>
              (CompileTimeResult(variable.value, variable.value, CompileTimeInspection(Set(variable))), true)
            case None => (evaluateCompileTime(target, scope, context), false)
          }
        } else {
          (evaluateCompileTime(target, scope, context), false)
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
          isFullyEvaluated(realEvalTarget, runMode) &&
            realEvalArguments.positional.forall { arg => isFullyEvaluated(arg, runMode) } &&
            realEvalArguments.named.view.values.forall { arg => isFullyEvaluated(arg, runMode) }

        if (canCallFunction) {
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
          val canEvaluateBasedOnPurity = !context.partialEvaluation || method.traits.contains(LambdaTrait.Pure)

          if (hasCompileTimeRunMode && canEvaluateBasedOnPurity) {
            val realValue = method.call(
              callContext,
              addSelfArgument(realEvalArguments, realEvalTarget),
              location
            )

            logger.debug(s"[compile-time] [call] Evaluated $codeValue to $realValue")

            return CompileTimeResult(realValue, realValue, CompileTimeInspection.empty)
          }
        }

        CompileTimeResult(codeValue, codeValue, targetInspection.combineWith(argumentInspections))
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
      case Value.Lambda(_, _) => value

      case Value.Operation(Operation.LambdaDefinition(params, body), location) =>
        val lambda = Lambda(
          params,
          body,

          LambdaInfo(
            scope,

            // Don't care about this at runtime for now
            scopeVariables = Set.empty,

            // We don't really care about other traits.
            // Once this function has reached runtime, it should be executable there
            traits = Set(LambdaTrait.Runtime)
          )
        )

        evaluateRuntime(Value.Lambda(lambda, location), scope)

      case Value.Operation(Operation.Block(values), location) =>
        val evaluatedValues = values.map(evaluateRuntime(_, scope))

        if (evaluatedValues.nonEmpty) {
          evaluatedValues.last
        } else {
          Value.Nothing(location)
        }

      case Value.Operation(Operation.Let(name, letValue, block), location) =>
        val variable = new Variable(name, Value.Unknown(location))
        val letScope = scope.newChild(Seq(variable))

        val evaluatedLetValue = evaluateRuntime(letValue, letScope)

        variable.dangerouslySetValue(evaluatedLetValue)

        evaluateRuntime(Value.Operation(block, location), letScope)

      case Value.Operation(Operation.NameReference(name), location) =>
        val foundValue = scope.find(name)

        foundValue match {
          case Some(variable) => variable.value
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val evaluatedArguments = Arguments(
          positional = arguments.positional.map(evaluateRuntime(_, scope)),
          named = arguments.named.view.mapValues(evaluateRuntime(_, scope)).toMap
        )

        val (evaluatedTarget, isVarCall) = if (mayBeVarCall) {
          scope.find(name) match {
            case Some(variable) => (variable.value, true)
            case None => (evaluateRuntime(target, scope), false)
          }
        } else {
          (evaluateRuntime(target, scope), false)
        }

        Core.nativeValueFor(evaluatedTarget).callOrThrowError(
          CallContext(this, runMode),
          if (isVarCall) { "call" } else { name },
          addSelfArgument(evaluatedArguments, evaluatedTarget),
          location
        )
    }
  }

  private def isFullyEvaluated(value: Value, runMode: RunMode): Boolean = {
    if (runMode != RunMode.CompileTime && runMode != RunMode.ParseTime) {
      throw new Error("isFullyEvaluated is only supposed to be called compile-time (for now)");
    }

    value match {
      case Value.Unknown(_) => false
      case Value.Nothing(_) => true
      case Value.Boolean(_, _) => true
      case Value.Int(_, _, _) => true
      case Value.Float(_, _) => true
      case Value.String(_, _) => true
      case Value.Native(_, _) => true
      case Value.Struct(_, _) => true

      case Value.Lambda(Lambda(_, _, info), _) =>
        val canBeCalledAtCompileTime = info.traits.contains(LambdaTrait.CompileTime)
        val variablesInScopeAreKnown = info.scopeVariables.forall { v => isFullyEvaluated(v.value, runMode) }

        canBeCalledAtCompileTime && variablesInScopeAreKnown

      case Value.Operation(_, _) => false
    }
  }

  private def uniqueVariableName(variable: Variable) = s"__${variable.name}__${variable.objectId}__"

  private def addSelfArgument(arguments: Arguments, self: Value) =
    Arguments(self +: arguments.positional, arguments.named)

  private def asBlock(value: Value): Block = {
    value match {
      case Value.Operation(block @ Operation.Block(_), _) => block
      case _ => Operation.Block(Seq(value))
    }
  }
}
