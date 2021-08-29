//package photon
//
//import com.typesafe.scalalogging.Logger
//import photon.frontend.{ASTBlock, ASTToValue, ASTValue, Parser}
//import photon.core.{CallContext, Core}
//import photon.interpreter.ValueWithScope
//
//sealed abstract class RunMode(val name: String) {}
//
//object RunMode {
//  case object Runtime extends RunMode("runtime")
//  case object CompileTime extends RunMode("compile-time")
//  case object ParseTime extends RunMode("parse-time")
//}
//
//case class EvalError(message: String, override val location: Option[Location])
//  extends PhotonError(message, location) {}
//
//case class CompileTimeContext(
//  partialEvaluation: Boolean,
//  callStack: Seq[CallStackEntry]
//)
//
//case class RuntimeContext(callStack: Seq[CallStackEntry])
//
//case class CompileTimeResult(
//  code: ValueWithScope,
//  real: ValueWithScope
//) {
//  def toScope(scope: Scope) = CompileTimeResult(
//    code.toScope(scope),
//    real.toScope(scope)
//  )
//}
//
//class Interpreter(val runMode: RunMode) {
//  private val logger = Logger[Interpreter]
//  private val core = new Core
//
//  private val MAX_RECURSIVE_CALLS = 8
//
//  def macroHandler(name: String, parser: Parser): Option[ASTValue] =
//    core.macroHandler(CallContext(this, RunMode.ParseTime, callStack = Seq.empty, callScope = core.rootScope), name, parser)
//
//  def evaluate(ast: ASTBlock): Value = {
//    val block = ASTToValue.transformBlock(ast, core.staticRootScope)
//
//    evaluate(Value.Operation(block, None))
//  }
//
//  def evaluate(ast: ASTValue): Value = {
//    val value = ASTToValue.transform(ast, core.staticRootScope)
//
//    evaluate(value)
//  }
//
//  def evaluate(value: Value): Value =
//    evaluate(value, core.rootScope, core.rootScope, runMode, callStack = Seq.empty)
//
//  def evaluate(value: Value, inScope: Scope, toScope: Scope, runMode: RunMode, callStack: Seq[CallStackEntry] = Seq.empty): Value =
//    runMode match {
//      case RunMode.Runtime => evaluateRuntime(value, inScope, RuntimeContext(callStack))
//      case RunMode.CompileTime | RunMode.ParseTime =>
//        val result = evaluateCompileTime(
//          value,
//          inScope,
//          CompileTimeContext(
//            partialEvaluation = false,
//            callStack
//          )
//        )
//
//        if (result.real.value.isFullyKnown) {
//          result.real.toScope(toScope).value
//        } else {
//          result.code.toScope(toScope).value
//        }
//    }
//
//  private def evaluateCompileTime(
//    value: Value,
//    scope: Scope,
//    context: CompileTimeContext
//  ): CompileTimeResult = {
//    value match {
//      case Value.Unknown(_) => ???
//
//      case Value.Nothing(_)    |
//           Value.Boolean(_, _) |
//           Value.Int(_, _, _)  |
//           Value.Float(_, _)   |
//           Value.String(_, _)  |
//           Value.Native(_, _)  |
//           Value.Struct(_, _)  =>
//        CompileTimeResult(
//          ValueWithScope(value, None),
//          ValueWithScope(value, None)
//        )
//
//        case Value.BoundFunction(BoundFunction(_, fnScope, _), _) => CompileTimeResult(
//          ValueWithScope(value, Some(fnScope)),
//          ValueWithScope(value, Some(fnScope))
//        )
//
////      case Value.Lambda(Lambda(params, body, info), location) =>
////        val evalScopeVariables = params.map { param =>
////          new Variable(param.name, Value.Unknown(location))
////        }
////        val evalScope = scope.newChild(evalScopeVariables)
////
////        val (renamedParams, evalRenames) = if (context.renameVariables) {
////          val renamedParams = params.zip(evalScopeVariables)
////            .map { case (param, variable) => Parameter(uniqueVariableName(variable), param.typeValue) }
////
////          val evalRenames = context.currentRenames ++ evalScopeVariables.map { variable => (variable, uniqueVariableName(variable)) }.toMap
////
////          (renamedParams, evalRenames)
////        } else {
////          (params, context.currentRenames)
////        }
////
////        val CompileTimeResult(codeEvalBody, realEvalBody, inspection) =
////          evaluateCompileTime(
////            Value.Operation(body, location),
////            evalScope,
////            CompileTimeContext(
////              partialEvaluation = true,
////              renameVariables = context.renameVariables,
////              evalRenames,
////              context.callStack
////            )
////          )
////
////        val resultInspection = inspection.withoutVariables(evalScopeVariables)
////
////        val lambdaInfo = LambdaInfo(
////          scope,
////          scopeVariables = resultInspection.nameUses,
////          traits = info.traits
////        )
////
////        val codeValue = Value.Lambda(Lambda(renamedParams, asBlock(codeEvalBody), lambdaInfo), location)
////        val realValue = if (isFullyEvaluated(realEvalBody, runMode)) {
////          Value.Lambda(Lambda(renamedParams, asBlock(realEvalBody), lambdaInfo), location)
////        } else {
////          codeValue
////        }
////
////        logger.debug(s"[compile-time] [lambda] Evaluated $value to $realValue")
////
////        CompileTimeResult(codeValue, realValue, resultInspection)
//
//      case Value.Operation(Operation.Function(fn), location) =>
//        val boundFn = BoundFunction(
//          fn,
//          traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure),
//          scope = scope
//        )
//
//        // TODO: Try to partially evaluate body
//
//        evaluateCompileTime(
//          Value.BoundFunction(boundFn, location),
//          scope,
//          context
//        )
//
//      case Value.Operation(Operation.Block(values), location) =>
//        val lastIndex = values.size - 1
//        val codeResultsBuilder = Seq.newBuilder[ValueWithScope]
//        val realResultsBuilder = Seq.newBuilder[ValueWithScope]
//
//        codeResultsBuilder.sizeHint(values.size)
//        realResultsBuilder.sizeHint(values.size)
//
//        values.zipWithIndex.foreach { case (value, index) =>
//          val CompileTimeResult(code, real) =
//            evaluateCompileTime(value, scope, context)
//
//          if (!real.value.isNothing || index == lastIndex) {
//            codeResultsBuilder.addOne(code)
//          }
//
//          if (real.value.isOperation || index == lastIndex) {
//            realResultsBuilder.addOne(real)
//          }
//        }
//
//        val codes = codeResultsBuilder.result()
//        val reals = realResultsBuilder.result()
//
//        val codeValue = if (codes.length == 1) {
//          codes.last
//        } else {
//          ValueWithScope(
//            Value.Operation(Operation.Block(codes.map(_.toScope(scope).value)), location),
//            Some(scope)
//          )
//        }
//        val realValue = if (reals.length == 1) {
//          reals.last
//        } else {
//          ValueWithScope(
//            Value.Operation(Operation.Block(reals.map(_.toScope(scope).value)), location),
//            Some(scope)
//          )
//        }
//
//        CompileTimeResult(codeValue, realValue)
//
//      case Value.Operation(Operation.Let(name, letValue, block), location) =>
//        val variable = new Variable(name, Value.Unknown(location))
//        val letScope = scope.newChild(Seq(variable))
//
//        // TODO: Make sure it is a compile-time error to directly use the reference in the expression
//        val letResult = evaluateCompileTime(letValue, letScope, context)
//        val realLetValueIsFullyKnown = letResult.real.value.isFullyKnown
//
//        variable.dangerouslySetValue(if (realLetValueIsFullyKnown) {
//          letResult.real.value
//        } else {
//          letResult.code.value
//        })
//
//        val blockResult = evaluateCompileTime(
//          Value.Operation(block, location),
//          letScope,
//          CompileTimeContext(
//            // TODO: I'm not completely sure this is exact, but should be ok
//            partialEvaluation = !realLetValueIsFullyKnown,
//            context.callStack
//          )
//        )
//
//        val realBlockValueIsFullyKnown = blockResult.real.value.isFullyKnown
//
//        val codeUsesVariable =
//          letResult.code.value.unboundNames.contains(name) ||
//            blockResult.code.value.unboundNames.contains(name)
//
//        // The let name is unused, can be eliminated if it's not an operation
//        // If it's an operation it probably can't be, because the expression may have side effects
//        val codeResult = if (!codeUsesVariable) {
//          // TODO: What if the realLetValue is a lambda which depends on a scope, like this:
//          //       `operation = somethingWithSideEffect(); () { operation() }`. Is this possible?
//          val shouldEliminateLetValue = !letResult.real.value.isOperation
//
//          // TODO: !codeLetValue.isPure?
//          if (shouldEliminateLetValue) {
//            blockResult.code
//          } else {
//            val blockWithLetExpressionForSideEffects = Operation.Block(
//              Seq(letResult.code.toScope(scope).value) ++ asBlock(blockResult.code.toScope(scope).value).values
//            )
//
//            ValueWithScope(
//              Value.Operation(blockWithLetExpressionForSideEffects, location),
//              Some(scope)
//            )
//          }
//        } else {
//          ValueWithScope(
//            Value.Operation(
//              Operation.Let(
//                name,
//                letResult.code.toScope(letScope).value,
//                asBlock(blockResult.code.toScope(letScope).value)
//              ),
//              location
//            ),
//            Some(scope)
//          )
//        }
//
//        val realResult = if (realLetValueIsFullyKnown && realBlockValueIsFullyKnown) {
//          logger.debug(s"[compile-time] [let] Evaluated $value to ${blockResult.real.value}")
//
//          blockResult.real
//        } else {
//          logger.debug(s"[compile-time] [let] Evaluated $value to ${codeResult.value}")
//
//          codeResult
//        }
//
//        CompileTimeResult(codeResult, realResult)
//
//      case Value.Operation(Operation.Reference(name), location) =>
//        val variable = scope.find(name) match {
//          case Some(variable) => variable
//          case None => throw EvalError(s"Invalid reference to $name", location)
//        }
//
//        if (variable.value.isUnknown) {
//          CompileTimeResult(
//            ValueWithScope(value, Some(scope)),
//            ValueWithScope(value, Some(scope)),
//          )
//        } else {
//          CompileTimeResult(
//            ValueWithScope(value, Some(scope)),
//            ValueWithScope(variable.value, Some(scope)),
//          )
//        }
//
//      case Value.Operation(Operation.Call(target, name, arguments), location) =>
//        val positionalEvals = arguments.positional.map { argument => evaluateCompileTime(argument, scope, context) }
//        val namedEvals = arguments.named.view.mapValues { argument => evaluateCompileTime(argument, scope, context) }.toMap
//
//        val CompileTimeResult(codeEvalTarget, realEvalTarget) = evaluateCompileTime(target, scope, context)
//
//        val positionalRealEvalArguments = positionalEvals.map { case CompileTimeResult(_, real) => real }
//        val namedRealEvalArguments = namedEvals.view.mapValues { case CompileTimeResult(_, real) => real }.toMap
//
//        // TODO: This is not 100% correct, we should be able to call methods on a partial struct
//        if (realEvalTarget.value.isFullyKnown) {
//          val hasFullyEvaluatedArguments = {
//            // TODO: There is room for optimization here, we're calling `isFullyKnown` multiple times
//            positionalRealEvalArguments.forall(_.value.isFullyKnown) &&
//              namedRealEvalArguments.view.values.forall(_.value.isFullyKnown)
//          }
//
//          val nativeValueForTarget = Core.nativeValueFor(realEvalTarget.value)
//
//          val method = nativeValueForTarget.method(
//            name,
//            location
//          ) match {
//            case Some(value) => value
//            case None => throw EvalError(s"Cannot call method $name on $realEvalTarget", location)
//          }
//
//          val reachedRecursiveCallLimit = context.callStack.count(_.methodId == method.methodId) >= MAX_RECURSIVE_CALLS
//          val hasCompileTimeRunMode = method.traits.contains(FunctionTrait.CompileTime)
//          val hasPartialRunMode = method.traits.contains(FunctionTrait.Partial)
//          val isPureFunction = method.traits.contains(FunctionTrait.Pure)
//          val canEvaluateBasedOnPurity = !context.partialEvaluation || isPureFunction
//
//          val canCallFunctionCompileTime =
//            hasCompileTimeRunMode &&
//              hasFullyEvaluatedArguments &&
//              canEvaluateBasedOnPurity &&
//              !reachedRecursiveCallLimit
//
//          if (hasPartialRunMode && !isPureFunction) {
//            throw EvalError(s"Partial method $name on $realEvalTarget must also be pure", location)
//          }
//
//          val canCallFunctionPartially =
//            hasPartialRunMode &&
//              canEvaluateBasedOnPurity &&
//              !reachedRecursiveCallLimit
//
//          if (canCallFunctionCompileTime) {
//            val realEvalArguments = Arguments(
//              positional = positionalRealEvalArguments.map(_.value),
//              named = namedRealEvalArguments.view.mapValues(_.value).toMap
//            )
//
//            val callContext = CallContext(
//              this,
//              runMode,
//
//              // FIXME: This should be the name of the function, not the name of the variable it was called through
//              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location)),
//              callScope = scope
//            )
//
//            // We're relying on the method call to return something that is adequate in `scope`
//            val realValue = method.call(
//              callContext,
//              addSelfArgument(realEvalArguments, realEvalTarget.value),
//              location
//            )
//
//            val realValueWithScope = ValueWithScope(realValue, Some(scope))
//
//            logger.debug(s"[compile-time] [call] Evaluated $value to $realValue")
//
//            return CompileTimeResult(realValueWithScope, realValueWithScope)
//          }
//
////          if (canCallFunctionPartially) {
////            logger.debug(s"[partial] [call] Can call $value partially")
////
////            // TODO: Not sure if the realvalues here need to be moveScope'd or not
////            val partialEvalArguments = Arguments(
////              positional = positionalEvals.map { case CompileTimeResult(codeValue, realValue) =>
////                if (realValue.value.isFullyKnown) realValue.value else codeValue
////              },
////              named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, realValue) =>
////                if (realValue.value.isFullyKnown) realValue.value else codeValue
////              }.toMap
////            )
////
////            val callContext = CallContext(
////              this,
////              runMode,
////
////              // FIXME: This should be the name of the function, not the name of the variable it was called through
////              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location)),
////              callScope = scope
////            )
////
////            val realValue = method.call(
////              callContext,
////              addSelfArgument(partialEvalArguments, realEvalTarget.value),
////              location
////            )
////
////            // TODO: Fix scope escape here
////
////            logger.debug(s"[partial] [call] Evaluated $value to $realValue")
////
////            // TODO: Should the codeValue here be `codeValue` or `realValue`?
////            return CompileTimeResult(realValue, ValueWithScope(realValue, scope))
////          }
//        }
//
//        val codeEvalArguments = Arguments(
//          positional = positionalEvals.map { case CompileTimeResult(code, _) => code.toScope(scope).value },
//          named = namedEvals.view.mapValues { case CompileTimeResult(code, _) => code.toScope(scope).value }.toMap
//        )
//
//        val codeValue = Value.Operation(
//          Operation.Call(codeEvalTarget.toScope(scope).value, name, codeEvalArguments),
//          location
//        )
//
//        CompileTimeResult(
//          ValueWithScope(codeValue, Some(scope)),
//          ValueWithScope(codeValue, Some(scope))
//        )
//    }
//  }
//
////  private def flattenBlocksDeep(block: Operation.Block): Operation.Block = {
////    val innerFlattenedValues = block.values.map(flattenBlocksDeep)
////    val flatBlock = flattenValuesForBlock(innerFlattenedValues)
////
////    Operation.Block(flatBlock)
////  }
////
////  private def flattenBlocksDeep(value: Value): Value = {
////    value match {
////      case Value.Unknown(_) |
////           Value.Nothing(_) |
////           Value.Boolean(_, _) |
////           Value.Int(_, _, _) |
////           Value.Float(_, _) |
////           Value.String(_, _) |
////           Value.Native(_, _) => value
////
////      case Value.Struct(Struct(props), location) =>
////        Value.Struct(
////          Struct(props.view.mapValues(flattenBlocksDeep).toMap),
////          location
////        )
////
////      // TODO: Make sure this is done when constructing the function
////      case Value.BoundFunction(_, _) => value
////      case Value.Operation(Operation.Function(_), _) => value
////
////      case Value.Operation(Operation.Reference(_), _) => value
////
////      case Value.Operation(Operation.Call(target, name, arguments), location) =>
////        Value.Operation(
////          Operation.Call(flattenBlocksDeep(target), name, arguments.map(flattenBlocksDeep)),
////          location
////        )
////
////      case Value.Operation(block @ Operation.Block(_), location) =>
////        Value.Operation(
////          flattenBlocksDeep(block),
////
////          // TODO: This location may not be correct anymore
////          location
////        )
////
////      case Value.Operation(Operation.Let(name, innerValue, innerBlock), location) =>
////        Value.Operation(
////          Operation.Let(name, flattenBlocksDeep(innerValue), flattenBlocksDeep(innerBlock)),
////          location
////        )
////    }
////  }
////
////  private def flattenValuesForBlock(values: Seq[Value]): Seq[Value] = {
////    val resultValues = Seq.newBuilder[Value]
////
////    breakable {
////      for (i <- values.indices) {
////        val value = values(i)
////
////        value match {
////          case Value.Unknown(_) |
////               Value.Nothing(_) |
////               Value.Boolean(_, _) |
////               Value.Int(_, _, _) |
////               Value.Float(_, _) |
////               Value.String(_, _) |
////               Value.Native(_, _) |
////               Value.Struct(_, _) |
////               Value.BoundFunction(_, _) |
////               Value.Operation(Operation.Reference(_), _) |
////               Value.Operation(Operation.Call(_, _, _), _) |
////               Value.Operation(Operation.Function(_), _)
////          => resultValues.addOne(value)
////
////          case Value.Operation(Operation.Block(innerValues), _) => resultValues.addAll(flattenValuesForBlock(innerValues))
////          case Value.Operation(Operation.Let(name, value, innerBlock), letLocation) =>
////            val restOfValues = values.slice(i + 1, values.size)
////            val newBlock = Operation.Block(flattenValuesForBlock(innerBlock.values ++ restOfValues))
////
////            // TODO: This location here may not be correct anymore
////            val newLet = Value.Operation(Operation.Let(name, value, newBlock), letLocation)
////
////            resultValues.addOne(newLet)
////
////            break
////        }
////      }
////    }
////
////    logger.debug(s"Flattened $values to ${resultValues.result}")
////
////    resultValues.result
////  }
//
////  private def inlineValue(value: Value, scope: Scope, renames: Map[VariableName, VariableName]): Value = {
////    value match {
////      case Value.Unknown(_) |
////           Value.Nothing(_) |
////           Value.Boolean(_, _) |
////           Value.Int(_, _, _) |
////           Value.Float(_, _) |
////           Value.String(_, _) |
////           Value.Native(_, _) => value
////
////      case Value.Struct(Struct(props), location) =>
////        Value.Struct(
////          Struct(
////            props.view.mapValues(inlineValue(_, scope, renames)).toMap
////          ),
////          location
////        )
////
////      case Value.BoundFunction(BoundFunction(fn, fromScope, traits), location) =>
////        // TODO: Serialize `traits` correctly
////        moveScope(
////          Value.Operation(Operation.Function(fn), location),
////          fromScope,
////          scope,
////          renames
////        )
////
////      case Value.Operation(Operation.Block(values), location) =>
////        Value.Operation(
////          Operation.Block(values.map(inlineValue(_, scope, renames))),
////          location
////        )
////
////      case Value.Operation(Operation.Let(name, letValue, block), location) =>
////        val newName = new VariableName(name.originalName)
////        val variable = new Variable(name, Value.Unknown(location))
////
////        val innerRenames = renames + (name -> newName)
////
////        val innerScope = scope.newChild(Seq(variable))
////        val newLetValue = inlineValue(letValue, innerScope, innerRenames)
////
////        variable.dangerouslySetValue(newLetValue)
////
////        val newBlock = Operation.Block(block.values.map(inlineValue(_, innerScope, innerRenames)))
////
////        Value.Operation(
////          Operation.Let(newName, newLetValue, newBlock),
////          location
////        )
////
////      case Value.Operation(Operation.Reference(name), location) =>
////        Value.Operation(
////          Operation.Reference(renames.getOrElse(name, name)),
////          location
////        )
////
////      case Value.Operation(Operation.Function(fn), location) =>
////        val fnBodyWithRenames = Operation.Block(fn.body.values.map(renameVariables(_, renames)))
////        val functionWithRenames = new Function(fn.params, fnBodyWithRenames)
////
////        Value.Operation(
////          Operation.Function(functionWithRenames),
////          location
////        )
////
////      case Value.Operation(Operation.Call(target, name, arguments), location) =>
////        Value.Operation(
////          Operation.Call(
////            target = inlineValue(target, scope, renames),
////            name,
////            arguments = Arguments(
////              positional = arguments.positional.map(inlineValue(_, scope, renames)),
////              named = arguments.named.map { case name -> value => name -> inlineValue(value, scope, renames) }
////            )
////          ),
////          location
////        )
////    }
////  }
//
//  private def evaluateRuntime(value: Value, scope: Scope, context: RuntimeContext): Value = {
//    value match {
//      case Value.Unknown(_) => ???
//      case Value.Nothing(_) => value
//      case Value.Boolean(_, _) => value
//      case Value.Int(_, _, _) => value
//      case Value.Float(_, _) => value
//      case Value.String(_, _) => value
//      case Value.Native(_, _) => value
//      case Value.Struct(_, _) => value
//      case Value.BoundFunction(_, _) => value
//
//      case Value.Operation(Operation.Function(fn), location) =>
//        val boundFn = BoundFunction(
//          fn,
//          scope,
//
//          // We don't really care about other traits.
//          // Once this function has reached runtime, it should be executable there
//          traits = Set(FunctionTrait.Runtime)
//        )
//
//        evaluateRuntime(Value.BoundFunction(boundFn, location), scope, context)
//
//      case Value.Operation(Operation.Block(values), location) =>
//        val evaluatedValues = values.map(evaluateRuntime(_, scope, context))
//
//        if (evaluatedValues.nonEmpty) {
//          evaluatedValues.last
//        } else {
//          Value.Nothing(location)
//        }
//
//      case Value.Operation(Operation.Let(name, letValue, block), location) =>
//        val variable = new Variable(name, Value.Unknown(location))
//        val letScope = scope.newChild(Seq(variable))
//
//        val evaluatedLetValue = evaluateRuntime(letValue, letScope, context)
//
//        variable.dangerouslySetValue(evaluatedLetValue)
//
//        evaluateRuntime(Value.Operation(block, location), letScope, context)
//
//      case Value.Operation(Operation.Reference(name), location) =>
//        val foundValue = scope.find(name)
//
//        foundValue match {
//          case Some(variable) => variable.value
//          case None => throw EvalError(s"Invalid reference to $name", location)
//        }
//
//      case Value.Operation(Operation.Call(target, name, arguments), location) =>
//        val evaluatedArguments = Arguments(
//          positional = arguments.positional.map(evaluateRuntime(_, scope, context)),
//          named = arguments.named.view.mapValues(evaluateRuntime(_, scope, context)).toMap
//        )
//
//        val evaluatedTarget = evaluateRuntime(target, scope, context)
//
//        Core.nativeValueFor(evaluatedTarget).callOrThrowError(
//          CallContext(this, runMode, context.callStack, callScope = scope),
//          name,
//          addSelfArgument(evaluatedArguments, evaluatedTarget),
//          location
//        )
//    }
//  }
//
//  private def addSelfArgument(arguments: Arguments, self: Value) =
//    Arguments(self +: arguments.positional, arguments.named)
//
//  private def asBlock(value: Value): Operation.Block = {
//    value match {
//      case Value.Operation(block @ Operation.Block(_), _) => block
//      case _ => Operation.Block(Seq(value))
//    }
//  }
//}
