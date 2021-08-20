package photon

import com.typesafe.scalalogging.Logger
import photon.frontend.{ASTBlock, ASTToValue, ASTValue, Parser}
import photon.core.{CallContext, Core}

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

case class ValueWithScope(value: Value, scope: Scope)

case class CompileTimeResult(
  codeValue: Value,
  realValue: ValueWithScope
)

class Interpreter(val runMode: RunMode) {
  private val logger = Logger[Interpreter]
  private val core = new Core

  private val MAX_RECURSIVE_CALLS = 8

  def macroHandler(name: String, parser: Parser): Option[ASTValue] =
    core.macroHandler(CallContext(this, RunMode.ParseTime, callStack = Seq.empty, callScope = core.rootScope), name, parser)

  def evaluate(ast: ASTBlock): Value = {
    val block = ASTToValue.transformBlock(ast, core.staticRootScope)

    evaluate(Value.Operation(block, None))
  }

  def evaluate(ast: ASTValue): Value = {
    val value = ASTToValue.transform(ast, core.staticRootScope)

    evaluate(value)
  }

  def evaluate(value: Value): Value =
    evaluate(value, core.rootScope, core.rootScope, runMode, callStack = Seq.empty)

  def evaluate(value: Value, inScope: Scope, toScope: Scope, runMode: RunMode, callStack: Seq[CallStackEntry] = Seq.empty): Value =
    runMode match {
      case RunMode.Runtime => evaluateRuntime(value, inScope, RuntimeContext(callStack))
      case RunMode.CompileTime | RunMode.ParseTime =>
        val result = evaluateCompileTime(
          value,
          inScope,
          CompileTimeContext(
            partialEvaluation = false,
            callStack
          )
        )

        if (result.realValue.value.isFullyKnown) {
          moveScope(result.realValue, toScope)
        } else {
          // TODO: Should `codeValue` be moved to the `toScope`?
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
      case Value.Nothing(_)    => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.Boolean(_, _) => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.Int(_, _, _)  => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.Float(_, _)   => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.String(_, _)  => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.Native(_, _)  => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.Struct(_, _)  => CompileTimeResult(value, ValueWithScope(value, scope))
      case Value.BoundFunction(_, _) => CompileTimeResult(value, ValueWithScope(value, scope))

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
        val realValuesBuilder = Seq.newBuilder[ValueWithScope]

        codeValuesBuilder.sizeHint(values.size)
        realValuesBuilder.sizeHint(values.size)

        values.zipWithIndex.foreach { case (value, index) =>
          val CompileTimeResult(codeValue, realValue) =
            evaluateCompileTime(value, scope, context)

          if (!realValue.value.isNothing || index == lastIndex) {
            codeValuesBuilder.addOne(codeValue)
          }

          if (realValue.value.isOperation || index == lastIndex) {
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
          ValueWithScope(
            value = Value.Operation(Operation.Block(realValues.map(moveScope(_, scope))), location),
            scope = scope
          )
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

        variable.dangerouslySetValue(realLetValue.value)

        val realLetValueIsFullyKnown = realLetValue.value.isFullyKnown

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

        val realBlockValueIsFullyKnown = realBlockValue.value.isFullyKnown

        val codeUsesVariable = codeLetValue.unboundNames.contains(name) || codeBlockValue.unboundNames.contains(name)

        // The let name is unused, can be eliminated if it's not an operation
        // If it's an operation it probably can't be, because the expression may have side effects
        val codeValue = if (!codeUsesVariable) {
          // TODO: What if the realLetValue is a lambda which depends on a scope, like this:
          //       `operation = somethingWithSideEffect(); () { operation() }`. Is this possible?
          val shouldEliminateLetValue = !realLetValue.value.isOperation

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

          ValueWithScope(Value.Unknown(location), scope)
        }

        CompileTimeResult(codeValue, realValue)

      case Value.Operation(Operation.Reference(name), location) =>
        val variable = scope.find(name) match {
          case Some(variable) => variable
          case None => throw EvalError(s"Invalid reference to $name", location)
        }

        val realValue = if (variable.value.isUnknown) { value } else { variable.value }

        CompileTimeResult(value, ValueWithScope(realValue, scope))

      case Value.Operation(Operation.Call(target, name, arguments), location) =>
        val positionalEvals = arguments.positional.map { argument => evaluateCompileTime(argument, scope, context) }
        val namedEvals = arguments.named.view.mapValues { argument => evaluateCompileTime(argument, scope, context) }.toMap

        val codeEvalArguments = Arguments(
          positional = positionalEvals.map { case CompileTimeResult(codeValue, _) => codeValue },
          named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, _) => codeValue }.toMap
        )

        val positionalRealEvalArguments = positionalEvals.map { case CompileTimeResult(_, realValue) => realValue }
        val namedRealEvalArguments = namedEvals.view.mapValues { case CompileTimeResult(_, realValue) => realValue }.toMap

        val CompileTimeResult(codeEvalTarget, realEvalTarget) = evaluateCompileTime(target, scope, context)

        val codeValue = Value.Operation(
          Operation.Call(codeEvalTarget, name, codeEvalArguments),
          location
        )

        // TODO: This is not 100% correct, we should be able to call methods on a partial struct
        if (realEvalTarget.value.isFullyKnown) {
          val hasFullyEvaluatedArguments = {
            // TODO: There is room for optimization here, we're calling `isFullyKnown` multiple times
            positionalRealEvalArguments.forall(_.value.isFullyKnown) &&
              namedRealEvalArguments.view.values.forall(_.value.isFullyKnown)
          }

          val nativeValueForTarget = Core.nativeValueFor(realEvalTarget.value)

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
            val realEvalArguments = Arguments(
              positional = positionalRealEvalArguments.map(_.value),
              named = namedRealEvalArguments.view.mapValues(_.value).toMap
            )

            val callContext = CallContext(
              this,
              runMode,

              // FIXME: This should be the name of the function, not the name of the variable it was called through
              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location)),
              callScope = scope
            )

            // We're relying on the method call to return something that is adequate in `scope`
            val realValue = method.call(
              callContext,
              addSelfArgument(realEvalArguments, realEvalTarget.value),
              location
            )

            val realValueWithScope = ValueWithScope(realValue, scope)

            logger.debug(s"[compile-time] [call] Evaluated $codeValue to $realValue")

            return CompileTimeResult(realValue, realValueWithScope)
          }

          if (canCallFunctionPartially) {
            logger.debug(s"[partial] [call] Can call $codeValue partially")

            // TODO: Not sure if the realvalues here need to be moveScope'd or not
            val partialEvalArguments = Arguments(
              positional = positionalEvals.map { case CompileTimeResult(codeValue, realValue) =>
                if (realValue.value.isFullyKnown) realValue.value else codeValue
              },
              named = namedEvals.view.mapValues { case CompileTimeResult(codeValue, realValue) =>
                if (realValue.value.isFullyKnown) realValue.value else codeValue
              }.toMap
            )

            val callContext = CallContext(
              this,
              runMode,

              // FIXME: This should be the name of the function, not the name of the variable it was called through
              callStack = context.callStack ++ Seq(CallStackEntry(method.methodId, name, location)),
              callScope = scope
            )

            val realValue = method.call(
              callContext,
              addSelfArgument(partialEvalArguments, realEvalTarget.value),
              location
            )

            // TODO: Fix scope escape here

            logger.debug(s"[partial] [call] Evaluated $codeValue to $realValue")

            // TODO: Should the codeValue here be `codeValue` or `realValue`?
            return CompileTimeResult(realValue, ValueWithScope(realValue, scope))
          }
        }

        CompileTimeResult(codeValue, ValueWithScope(codeValue, scope))
    }
  }

//  private def flattenBlocksDeep(block: Operation.Block): Operation.Block = {
//    val innerFlattenedValues = block.values.map(flattenBlocksDeep)
//    val flatBlock = flattenValuesForBlock(innerFlattenedValues)
//
//    Operation.Block(flatBlock)
//  }
//
//  private def flattenBlocksDeep(value: Value): Value = {
//    value match {
//      case Value.Unknown(_) |
//           Value.Nothing(_) |
//           Value.Boolean(_, _) |
//           Value.Int(_, _, _) |
//           Value.Float(_, _) |
//           Value.String(_, _) |
//           Value.Native(_, _) => value
//
//      case Value.Struct(Struct(props), location) =>
//        Value.Struct(
//          Struct(props.view.mapValues(flattenBlocksDeep).toMap),
//          location
//        )
//
//      // TODO: Make sure this is done when constructing the function
//      case Value.BoundFunction(_, _) => value
//      case Value.Operation(Operation.Function(_), _) => value
//
//      case Value.Operation(Operation.Reference(_), _) => value
//
//      case Value.Operation(Operation.Call(target, name, arguments), location) =>
//        Value.Operation(
//          Operation.Call(flattenBlocksDeep(target), name, arguments.map(flattenBlocksDeep)),
//          location
//        )
//
//      case Value.Operation(block @ Operation.Block(_), location) =>
//        Value.Operation(
//          flattenBlocksDeep(block),
//
//          // TODO: This location may not be correct anymore
//          location
//        )
//
//      case Value.Operation(Operation.Let(name, innerValue, innerBlock), location) =>
//        Value.Operation(
//          Operation.Let(name, flattenBlocksDeep(innerValue), flattenBlocksDeep(innerBlock)),
//          location
//        )
//    }
//  }
//
//  private def flattenValuesForBlock(values: Seq[Value]): Seq[Value] = {
//    val resultValues = Seq.newBuilder[Value]
//
//    breakable {
//      for (i <- values.indices) {
//        val value = values(i)
//
//        value match {
//          case Value.Unknown(_) |
//               Value.Nothing(_) |
//               Value.Boolean(_, _) |
//               Value.Int(_, _, _) |
//               Value.Float(_, _) |
//               Value.String(_, _) |
//               Value.Native(_, _) |
//               Value.Struct(_, _) |
//               Value.BoundFunction(_, _) |
//               Value.Operation(Operation.Reference(_), _) |
//               Value.Operation(Operation.Call(_, _, _), _) |
//               Value.Operation(Operation.Function(_), _)
//          => resultValues.addOne(value)
//
//          case Value.Operation(Operation.Block(innerValues), _) => resultValues.addAll(flattenValuesForBlock(innerValues))
//          case Value.Operation(Operation.Let(name, value, innerBlock), letLocation) =>
//            val restOfValues = values.slice(i + 1, values.size)
//            val newBlock = Operation.Block(flattenValuesForBlock(innerBlock.values ++ restOfValues))
//
//            // TODO: This location here may not be correct anymore
//            val newLet = Value.Operation(Operation.Let(name, value, newBlock), letLocation)
//
//            resultValues.addOne(newLet)
//
//            break
//        }
//      }
//    }
//
//    logger.debug(s"Flattened $values to ${resultValues.result}")
//
//    resultValues.result
//  }

//  private def inlineValue(value: Value, scope: Scope, renames: Map[VariableName, VariableName]): Value = {
//    value match {
//      case Value.Unknown(_) |
//           Value.Nothing(_) |
//           Value.Boolean(_, _) |
//           Value.Int(_, _, _) |
//           Value.Float(_, _) |
//           Value.String(_, _) |
//           Value.Native(_, _) => value
//
//      case Value.Struct(Struct(props), location) =>
//        Value.Struct(
//          Struct(
//            props.view.mapValues(inlineValue(_, scope, renames)).toMap
//          ),
//          location
//        )
//
//      case Value.BoundFunction(BoundFunction(fn, fromScope, traits), location) =>
//        // TODO: Serialize `traits` correctly
//        moveScope(
//          Value.Operation(Operation.Function(fn), location),
//          fromScope,
//          scope,
//          renames
//        )
//
//      case Value.Operation(Operation.Block(values), location) =>
//        Value.Operation(
//          Operation.Block(values.map(inlineValue(_, scope, renames))),
//          location
//        )
//
//      case Value.Operation(Operation.Let(name, letValue, block), location) =>
//        val newName = new VariableName(name.originalName)
//        val variable = new Variable(name, Value.Unknown(location))
//
//        val innerRenames = renames + (name -> newName)
//
//        val innerScope = scope.newChild(Seq(variable))
//        val newLetValue = inlineValue(letValue, innerScope, innerRenames)
//
//        variable.dangerouslySetValue(newLetValue)
//
//        val newBlock = Operation.Block(block.values.map(inlineValue(_, innerScope, innerRenames)))
//
//        Value.Operation(
//          Operation.Let(newName, newLetValue, newBlock),
//          location
//        )
//
//      case Value.Operation(Operation.Reference(name), location) =>
//        Value.Operation(
//          Operation.Reference(renames.getOrElse(name, name)),
//          location
//        )
//
//      case Value.Operation(Operation.Function(fn), location) =>
//        val fnBodyWithRenames = Operation.Block(fn.body.values.map(renameVariables(_, renames)))
//        val functionWithRenames = new Function(fn.params, fnBodyWithRenames)
//
//        Value.Operation(
//          Operation.Function(functionWithRenames),
//          location
//        )
//
//      case Value.Operation(Operation.Call(target, name, arguments), location) =>
//        Value.Operation(
//          Operation.Call(
//            target = inlineValue(target, scope, renames),
//            name,
//            arguments = Arguments(
//              positional = arguments.positional.map(inlineValue(_, scope, renames)),
//              named = arguments.named.map { case name -> value => name -> inlineValue(value, scope, renames) }
//            )
//          ),
//          location
//        )
//    }
//  }

  private def moveScope(value: ValueWithScope, to: Scope): Value = {
    if (value.scope eq to) {
      return value.value
    }

    moveScope(value.value, value.scope, to, Map.empty)
  }

  private def moveScope(value: Value, from: Scope, to: Scope, renames: Map[VariableName, VariableName]): Value = {
    val namesToMove = detectNamesToMove(value.unboundNames, from, to)

    var currentScope: Option[Scope] = Some(from)
    var valueWithLets = value

    do {
      val scope = currentScope.get
      val namesInScopeToMove = scope.variables.keySet.intersect(namesToMove)
      val variablesToMove = namesInScopeToMove.map(scope.variables.get).map(_.get)

      valueWithLets = variablesToMove.foldLeft(valueWithLets) { case (result, variable) =>
        Value.Operation(
          Operation.Let(
            name = variable.name,
            value = variable.value,
            block = result match {
              case Value.Operation(block @ Operation.Block(_), _) => block
              case _ => Operation.Block(Seq(result))
            }
          ),
          result.location
        )
      }

      currentScope = scope.parent
    } while (currentScope.isDefined)

    val additionalRenames = namesToMove
      .map { oldName => oldName -> new VariableName(oldName.originalName) }
      .toMap

    renameAndUnbind(valueWithLets, renames ++ additionalRenames)
  }

  private def renameAndUnbind(value: Value, renames: Map[VariableName, VariableName]): Value = value match {
    case Value.Unknown(_) |
         Value.Nothing(_) |
         Value.Boolean(_, _) |
         Value.Int(_, _, _) |
         Value.Float(_, _) |
         Value.String(_, _) |
         Value.Native(_, _) => value

    case Value.Struct(Struct(values), location) =>
      Value.Struct(
        Struct(values.view.mapValues(renameAndUnbind(_, renames)).toMap),
        location
      )

    case Value.BoundFunction(BoundFunction(fn, scope, traits), location) =>
      // TODO: Serialize traits correctly
      // TODO: Make sure the bound function's scope is expected?
      // throw EvalError("Encountered BoundFn during renaming", location)
      renameAndUnbind(
        Value.Operation(
          Operation.Function(fn),
          location
        ),
        renames
      )

    case Value.Operation(Operation.Block(values), location) =>
      Value.Operation(
        Operation.Block(values.map(renameAndUnbind(_, renames))),
        location
      )

    case Value.Operation(Operation.Let(name, letValue, block), location) =>
      val newName = new VariableName(name.originalName)
      val innerRenames = renames + (name -> newName)

      val newLetValue = renameAndUnbind(letValue, innerRenames)
      val newBlock = Operation.Block(block.values.map(renameAndUnbind(_, innerRenames)))

      Value.Operation(
        Operation.Let(newName, newLetValue, newBlock),
        location
      )

    case Value.Operation(Operation.Reference(name), location) =>
      Value.Operation(
        Operation.Reference(renames.getOrElse(name, name)),
        location
      )

    case Value.Operation(Operation.Function(fn), location) =>
      val fnBodyWithRenames = Operation.Block(fn.body.values.map(renameAndUnbind(_, renames)))

      // TODO: Rename function parameters?
      val functionWithRenames = new Function(fn.params, fnBodyWithRenames)

      Value.Operation(
        Operation.Function(functionWithRenames),
        location
      )

    case Value.Operation(Operation.Call(target, name, arguments), location) =>
      Value.Operation(
        Operation.Call(
          target = renameAndUnbind(target, renames),
          name,
          arguments = arguments.map(renameAndUnbind(_, renames))
        ),
        location
      )
  }

  private def detectNamesToMove(unboundNames: Set[VariableName], from: Scope, to: Scope): Set[VariableName] = {
    unboundNames
      .filter(to.find(_).isEmpty)
      .map(from.find(_).get)
      .flatMap { variable =>
        if (unboundNames.contains(variable.name)) {
          Set(variable.name)
        } else {
          detectNamesToMove(variable.value.unboundNames, from, to) + variable.name
        }
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
          CallContext(this, runMode, context.callStack, callScope = scope),
          name,
          addSelfArgument(evaluatedArguments, evaluatedTarget),
          location
        )
    }
  }

  private def addSelfArgument(arguments: Arguments, self: Value) =
    Arguments(self +: arguments.positional, arguments.named)

  private def asBlock(value: Value): Operation.Block = {
    value match {
      case Value.Operation(block @ Operation.Block(_), _) => block
      case _ => Operation.Block(Seq(value))
    }
  }
}
