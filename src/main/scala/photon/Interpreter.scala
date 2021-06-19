package photon

import com.typesafe.scalalogging.Logger
import photon.Operation.Block
import photon.frontend.{ASTBlock, ASTToValue, ASTValue, Parser}
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

case class CompileTimeContext(
  partialEvaluation: Boolean,
  callStack: Seq[CallStackEntry]
)

case class RuntimeContext(callStack: Seq[CallStackEntry])

case class CompileTimeResult(
  codeValue: Value,
  realValue: Value
)

class Interpreter(val runMode: RunMode) {
  private val logger = Logger[Interpreter]
  private val core = new Core

  private val MAX_RECURSIVE_CALLS = 8

  def macroHandler(name: String, parser: Parser): Option[ASTValue] =
    core.macroHandler(CallContext(this, RunMode.ParseTime, callStack = Seq.empty), name, parser)

  def evaluate(ast: ASTBlock): Value = {
    val block = ASTToValue.transformBlock(ast, core.staticRootScope)

    evaluate(Value.Operation(block, None))
  }

  def evaluate(ast: ASTValue): Value = {
    val value = ASTToValue.transform(ast, core.staticRootScope)

    evaluate(value)
  }

  def evaluate(value: Value): Value =
    evaluate(value, core.rootScope, runMode, callStack = Seq.empty)

  def evaluate(value: Value, scope: Scope, runMode: RunMode, callStack: Seq[CallStackEntry] = Seq.empty): Value =
    runMode match {
      case RunMode.Runtime => evaluateRuntime(value, scope, RuntimeContext(callStack))
      case RunMode.CompileTime | RunMode.ParseTime =>
        val result = evaluateCompileTime(
          value,
          scope,
          CompileTimeContext(
            partialEvaluation = false,
            callStack
          )
        )

        if (result.realValue.isFullyKnown) result.realValue else result.codeValue
    }

  private def evaluateCompileTime(
    value: Value,
    scope: Scope,
    context: CompileTimeContext
  ): CompileTimeResult = {
    value match {
      case Value.Unknown(_) => ???
      case Value.Nothing(_) => CompileTimeResult(value, value)
      case Value.Boolean(_, _) => CompileTimeResult(value, value)
      case Value.Int(_, _, _) => CompileTimeResult(value, value)
      case Value.Float(_, _) => CompileTimeResult(value, value)
      case Value.String(_, _) => CompileTimeResult(value, value)
      case Value.Native(_, _) => CompileTimeResult(value, value)
      case Value.Struct(_, _) => CompileTimeResult(value, value)
      case Value.BoundFunction(_, _) => CompileTimeResult(value, value)

//      case Value.Lambda(Lambda(params, body, info), location) =>
//        val evalScopeVariables = params.map { param =>
//          new Variable(param.name, Value.Unknown(location))
//        }
//        val evalScope = scope.newChild(evalScopeVariables)
//
//        val (renamedParams, evalRenames) = if (context.renameVariables) {
//          val renamedParams = params.zip(evalScopeVariables)
//            .map { case (param, variable) => Parameter(uniqueVariableName(variable), param.typeValue) }
//
//          val evalRenames = context.currentRenames ++ evalScopeVariables.map { variable => (variable, uniqueVariableName(variable)) }.toMap
//
//          (renamedParams, evalRenames)
//        } else {
//          (params, context.currentRenames)
//        }
//
//        val CompileTimeResult(codeEvalBody, realEvalBody, inspection) =
//          evaluateCompileTime(
//            Value.Operation(body, location),
//            evalScope,
//            CompileTimeContext(
//              partialEvaluation = true,
//              renameVariables = context.renameVariables,
//              evalRenames,
//              context.callStack
//            )
//          )
//
//        val resultInspection = inspection.withoutVariables(evalScopeVariables)
//
//        val lambdaInfo = LambdaInfo(
//          scope,
//          scopeVariables = resultInspection.nameUses,
//          traits = info.traits
//        )
//
//        val codeValue = Value.Lambda(Lambda(renamedParams, asBlock(codeEvalBody), lambdaInfo), location)
//        val realValue = if (isFullyEvaluated(realEvalBody, runMode)) {
//          Value.Lambda(Lambda(renamedParams, asBlock(realEvalBody), lambdaInfo), location)
//        } else {
//          codeValue
//        }
//
//        logger.debug(s"[compile-time] [lambda] Evaluated $value to $realValue")
//
//        CompileTimeResult(codeValue, realValue, resultInspection)

      case Value.Operation(Operation.Function(fn), location) =>
        val boundFn = BoundFunction(
          fn,
          traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure),
          scope = scope
        )

        // TODO: Try to partially evaluate body

        evaluateCompileTime(
          Value.BoundFunction(boundFn, location),
          scope,
          context
        )

      case Value.Operation(Operation.Block(values), location) =>
        val lastIndex = values.size - 1
        val codeValuesBuilder = Seq.newBuilder[Value]
        val realValuesBuilder = Seq.newBuilder[Value]

        codeValuesBuilder.sizeHint(values.size)
        realValuesBuilder.sizeHint(values.size)

        values.zipWithIndex.foreach { case (value, index) =>
          val CompileTimeResult(codeValue, realValue) =
            evaluateCompileTime(value, scope, context)

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
        val variable = new Variable(name, Value.Unknown(location))
        val letScope = scope.newChild(Seq(variable))

        // TODO: Make sure it is a compile-time error to directly use the reference in the expression
        val CompileTimeResult(codeLetValue, realLetValue) =
          evaluateCompileTime(
            letValue,
            letScope,
            context
          )

        variable.dangerouslySetValue(realLetValue)

        val realLetValueIsFullyKnown = realLetValue.isFullyKnown

        val CompileTimeResult(codeBlockValue, realBlockValue) =
          evaluateCompileTime(
            Value.Operation(block, location),
            letScope,
            CompileTimeContext(
              // TODO: I'm not completely sure this is exact, but should be ok
              partialEvaluation = !realLetValueIsFullyKnown,
              context.callStack
            )
          )

        val realBlockValueIsFullyKnown = realBlockValue.isFullyKnown

        val codeUsesVariable = codeLetValue.unboundNames.contains(name) || codeBlockValue.unboundNames.contains(name)

        // The let name is unused, can be eliminated if it's not an operation
        // If it's an operation it probably can't be, because the expression may have side effects
        val codeValue = if (!codeUsesVariable) {
          val shouldEliminateLetValue = !realLetValue.isOperation

          // TODO: !codeLetValue.isPure?
          if (shouldEliminateLetValue) {
            Value.Operation(asBlock(codeBlockValue), location)
          } else {
            val blockWithLetExpressionForSideEffects = Operation.Block(
              Seq(codeLetValue) ++ asBlock(codeBlockValue).values
            )

            Value.Operation(blockWithLetExpressionForSideEffects, location)
          }
        } else {
          Value.Operation(Operation.Let(name, codeLetValue, asBlock(codeBlockValue)), location)
        }

        val realValue = if (realLetValueIsFullyKnown && realBlockValueIsFullyKnown) {
          logger.debug(s"[compile-time] [let] Evaluated $value to $realBlockValue")

          realBlockValue
        } else {
          logger.debug(s"[compile-time] [let] Evaluated $value to $codeValue")

          Value.Unknown(location)
        }

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.Reference(name), location) =>
        val variable = scope.find(name) match {
          case Some(variable) => variable
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

        val realValue = if (variable.value.isUnknown) { value } else { variable.value }

        CompileTimeResult(value, realValue)

      case Value.Operation(Operation.Call(target, name, arguments), location) =>
        val positionalEvals = arguments.positional.map { argument => evaluateCompileTime(argument, scope, context) }
        val namedEvals = arguments.named.view.mapValues { argument => evaluateCompileTime(argument, scope, context) }.toMap

        val codeEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(codeValue, _) => codeValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, _) => codeValue }.toMap
        )
        val realEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(_, realValue) => realValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(_, realValue) => realValue }.toMap
        )

        val partialEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(codeValue, realValue) =>
            if (realValue.isFullyKnown) realValue else codeValue
          },
          named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, realValue) =>
            if (realValue.isFullyKnown) realValue else codeValue
          }.toMap
        )

        val CompileTimeResult(codeEvalTarget, realEvalTarget) = evaluateCompileTime(target, scope, context)

        val codeValue = Value.Operation(
          Operation.Call(codeEvalTarget, name, codeEvalArguments),
          location
        )

        // TODO: This is not 100% correct, we should be able to call methods on a partial struct
        if (realEvalTarget.isFullyKnown) {
          val hasFullyEvaluatedArguments = {
            // TODO: There is room for optimization here, we're calling `isFullyKnown` multiple times
            realEvalArguments.positional.forall(_.isFullyKnown) &&
              realEvalArguments.named.view.values.forall(_.isFullyKnown)
          }

          val nativeValueForTarget = Core.nativeValueFor(realEvalTarget)

          val method = nativeValueForTarget.method(
            name,
            location
          ) match {
            case Some(value) => value
            case None => throw EvalError(s"Cannot call method $name on $realEvalTarget", location)
          }

          val reachedRecursiveCallLimit = context.callStack.count(_.methodId == method.methodId) >= MAX_RECURSIVE_CALLS
          val hasCompileTimeRunMode = method.traits.contains(FunctionTrait.CompileTime)
          val hasPartialRunMode = method.traits.contains(FunctionTrait.Partial)
          val isPureFunction = method.traits.contains(FunctionTrait.Pure)
          val canEvaluateBasedOnPurity = !context.partialEvaluation || isPureFunction

          val canCallFunctionCompileTime =
            hasCompileTimeRunMode &&
              hasFullyEvaluatedArguments &&
              canEvaluateBasedOnPurity &&
              !reachedRecursiveCallLimit

          if (hasPartialRunMode && !isPureFunction) {
            throw EvalError(s"Partial method $name on $realEvalTarget must also be pure", location)
          }

          val canCallFunctionPartially =
            hasPartialRunMode &&
              canEvaluateBasedOnPurity &&
              !reachedRecursiveCallLimit

          if (canCallFunctionCompileTime) {
            val callContext = CallContext(
              this,
              runMode,

              // FIXME: This should be the name of the function, not the name of the variable it was called through
              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location))
            )

            val realValue = method.call(
              callContext,
              addSelfArgument(realEvalArguments, realEvalTarget),
              location
            )

            logger.debug(s"[compile-time] [call] Evaluated $codeValue to $realValue")

            return CompileTimeResult(realValue, realValue)
          }

          if (canCallFunctionPartially) {
            logger.debug(s"[partial] [call] Can call $codeValue partially")

            val callContext = CallContext(
              this,
              runMode,

              // FIXME: This should be the name of the function, not the name of the variable it was called through
              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location))
            )

            val realValue = method.call(
              callContext,
              addSelfArgument(partialEvalArguments, realEvalTarget),
              location
            )

            logger.debug(s"[partial] [call] Evaluated $codeValue to $realValue")

            // TODO: Should the codeValue here be `codeValue` or `realValue`?
            return CompileTimeResult(realValue, realValue)
          }
        }

        CompileTimeResult(codeValue, codeValue)
    }
  }

  private def evaluateRuntime(value: Value, scope: Scope, context: RuntimeContext): Value = {
    value match {
      case Value.Unknown(_) => ???
      case Value.Nothing(_) => value
      case Value.Boolean(_, _) => value
      case Value.Int(_, _, _) => value
      case Value.Float(_, _) => value
      case Value.String(_, _) => value
      case Value.Native(_, _) => value
      case Value.Struct(_, _) => value
      case Value.BoundFunction(_, _) => value

      case Value.Operation(Operation.Function(fn), location) =>
        val boundFn = BoundFunction(
          fn,
          scope,

          // We don't really care about other traits.
          // Once this function has reached runtime, it should be executable there
          traits = Set(FunctionTrait.Runtime)
        )

        evaluateRuntime(Value.BoundFunction(boundFn, location), scope, context)

      case Value.Operation(Operation.Block(values), location) =>
        val evaluatedValues = values.map(evaluateRuntime(_, scope, context))

        if (evaluatedValues.nonEmpty) {
          evaluatedValues.last
        } else {
          Value.Nothing(location)
        }

      case Value.Operation(Operation.Let(name, letValue, block), location) =>
        val variable = new Variable(name, Value.Unknown(location))
        val letScope = scope.newChild(Seq(variable))

        val evaluatedLetValue = evaluateRuntime(letValue, letScope, context)

        variable.dangerouslySetValue(evaluatedLetValue)

        evaluateRuntime(Value.Operation(block, location), letScope, context)

      case Value.Operation(Operation.Reference(name), location) =>
        val foundValue = scope.find(name)

        foundValue match {
          case Some(variable) => variable.value
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

      case Value.Operation(Operation.Call(target, name, arguments), location) =>
        val evaluatedArguments = Arguments(
          positional = arguments.positional.map(evaluateRuntime(_, scope, context)),
          named = arguments.named.view.mapValues(evaluateRuntime(_, scope, context)).toMap
        )

        val evaluatedTarget = evaluateRuntime(target, scope, context)

        Core.nativeValueFor(evaluatedTarget).callOrThrowError(
          CallContext(this, runMode, context.callStack),
          name,
          addSelfArgument(evaluatedArguments, evaluatedTarget),
          location
        )
    }
  }

//  private def inspect(value: Value, scope: Scope): CompileTimeInspection = {
//    value match {
//      case Value.Unknown(_) => CompileTimeInspection.empty
//      case Value.Nothing(_) => CompileTimeInspection.empty
//      case Value.Boolean(_, _) => CompileTimeInspection.empty
//      case Value.Int(_, _, _) => CompileTimeInspection.empty
//      case Value.Float(_, _) => CompileTimeInspection.empty
//      case Value.String(_, _) => CompileTimeInspection.empty
//
//      // TODO: Should this have some sort of support for compile-time inspections?
//      case Value.Native(_, _) => CompileTimeInspection.empty
//
//      case Value.Struct(struct, _) =>
//        struct.props.values
//          .map(inspect(_, scope))
//          .fold(CompileTimeInspection.empty) { case (a, b) => a.combineWith(b) }
//
//      case Value.Lambda(lambda, location) =>
//        val lambdaVariables = lambda.params
//          .map(_.name)
//          .map(new Variable(_, Value.Unknown(location)))
//
//        val bodyScope = scope.newChild(lambdaVariables)
//        val bodyInspections = lambda.body.values
//          .map(inspect(_, bodyScope))
//          .fold(CompileTimeInspection.empty) { case (a, b) => a.combineWith(b) }
//
//        bodyInspections.withoutVariables(lambdaVariables)
//
//      case Value.Operation(Operation.Block(values), _) =>
//        values
//          .map(inspect(_, scope))
//          .fold(CompileTimeInspection.empty) { case (a, b) => a.combineWith(b) }
//
//      // TODO: Eliminate code duplication
//      case Value.Operation(Operation.LambdaDefinition(params, body), location) =>
//        val lambdaVariables = params
//          .map(_.name)
//          .map(new Variable(_, Value.Unknown(location)))
//
//        val bodyScope = scope.newChild(lambdaVariables)
//        val bodyInspections = body.values
//          .map(inspect(_, bodyScope))
//          .fold(CompileTimeInspection.empty) { case (a, b) => a.combineWith(b) }
//
//        bodyInspections.withoutVariables(lambdaVariables)
//
//      case Value.Operation(Operation.Let(name, letValue, block), location) =>
//        val variable = new Variable(name, Value.Unknown(location))
//        val letScope = scope.newChild(Seq(variable))
//
//        val letValueInspection = inspect(letValue, letScope)
//        val blockInspection = inspect(Value.Operation(block, location), letScope)
//
//        letValueInspection
//          .combineWith(blockInspection)
//          .withoutVariables(Seq(variable))
//
//      case Value.Operation(Operation.NameReference(name), location) =>
//        val variable = scope.find(name) match {
//          case Some(variable) => variable
//          case None => throw EvalError(s"Cannot find variable $name during inspection", location)
//        }
//
//        CompileTimeInspection(Set(variable))
//
//      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), _) =>
//        var inspection = CompileTimeInspection.empty
//
//        val isVarCall = if (mayBeVarCall) {
//          scope.find(name) match {
//            case Some(variable) =>
//              inspection = inspection.combineWith(CompileTimeInspection(Set(variable)))
//              true
//            case None => false
//          }
//        } else false
//
//        if (!isVarCall) {
//          inspection = inspection.combineWith(inspect(target, scope))
//        }
//
//        inspection = arguments.positional
//          .map(inspect(_, scope))
//          .fold(inspection) { case (a, b) => a.combineWith(b) }
//
//        inspection = arguments.named.values
//          .map(inspect(_, scope))
//          .fold(inspection) { case (a, b) => a.combineWith(b) }
//
//        inspection
//    }
//  }

//  private def isFullyEvaluated(value: Value, runMode: RunMode, referencedLambdaIds: Seq[ObjectId] = Seq.empty): Boolean = {
//    if (runMode != RunMode.CompileTime && runMode != RunMode.ParseTime) {
//      throw new Error("isFullyEvaluated is only supposed to be called compile-time (for now)");
//    }
//
//    value match {
//      case Value.Unknown(_) => false
//      case Value.Nothing(_) => true
//      case Value.Boolean(_, _) => true
//      case Value.Int(_, _, _) => true
//      case Value.Float(_, _) => true
//      case Value.String(_, _) => true
//      case Value.Native(_, _) => true
//      case Value.Struct(struct, _) =>
//        struct.props.view.values.forall(isFullyEvaluated(_, runMode, referencedLambdaIds))
//
//      case Value.Lambda(lambda, _) =>
//        val recursivelyReferencesItself = referencedLambdaIds.contains(lambda.objectId)
//
//        if (recursivelyReferencesItself) {
//          return true
//        }
//
//        val canBeCalledAtCompileTime = lambda.info.traits.contains(LambdaTrait.CompileTime)
//        val variablesInScopeAreKnown = lambda.info.scopeVariables.forall { v =>
//          v.value match {
//            case value @ Value.Lambda(referencedLambda, _) => isFullyEvaluated(
//              value,
//              runMode,
//              referencedLambdaIds :+ referencedLambda.objectId
//            )
//            case value => isFullyEvaluated(value, runMode, referencedLambdaIds)
//          }
//        }
//
//        canBeCalledAtCompileTime && variablesInScopeAreKnown
//
//      case Value.Operation(_, _) => false
//    }
//  }

//  private def uniqueVariableName(variable: Variable) = s"__${variable.name}__${variable.objectId}__"

  private def addSelfArgument(arguments: Arguments, self: Value) =
    Arguments(self +: arguments.positional, arguments.named)

  private def asBlock(value: Value): Block = {
    value match {
      case Value.Operation(block @ Operation.Block(_), _) => block
      case _ => Operation.Block(Seq(value))
    }
  }
}
