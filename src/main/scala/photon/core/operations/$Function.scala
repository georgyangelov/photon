package photon.core.operations

import photon.Core
import photon.base._
import photon.core._
import photon.frontend._

sealed abstract class FunctionRunMode
object FunctionRunMode {
  object CompileTimeOnly extends FunctionRunMode
  object Default         extends FunctionRunMode
  object PreferRunTime   extends FunctionRunMode
  object RunTimeOnly     extends FunctionRunMode
}

sealed abstract class InlinePreference
object InlinePreference {
  object ForceInline extends InlinePreference
  object Default     extends InlinePreference
  object NoInline    extends InlinePreference
}

sealed trait FunctionEvalRequest
object FunctionEvalRequest {
  case object ForceEval extends FunctionEvalRequest
  case object InlineIfPossible extends FunctionEvalRequest
  case object InlineIfDefinedInline extends FunctionEvalRequest
}

object $FunctionDef extends Type {
  override def typ = $Type
  override def toUValue(core: Core): UValue = inconvertible
  override val methods = Map.empty

  case class Value(fn: UFunction, scope: Scope, location: Option[Location]) extends EValue {
    override def typ: Type = $FunctionDef
    override def isOperation = true
    override def unboundNames = fn.unboundNames
    override def evalMayHaveSideEffects = false
    override def toUValue(core: Core) = UOperation.Function(fn, location)
    override def realType = evaluated.typ

    override def evaluate(mode: EvalMode) = {
      val context = EValue.context
      val eParams = fn.params.map { param =>
        val argType = context.toEPattern(param.typ, scope)

        EParameter(param.outName, param.inName, argType, location)
      }

      val eReturnType = fn.returnType
        .map(context.toEValue(_, scope))
        .getOrElse { inferReturnType(context, eParams) }

      val functionType = $FunctionT(eParams, eReturnType, FunctionRunMode.Default, InlinePreference.Default)

      FunctionValue(functionType, fn.nameMap, fn.body, scope, location)
    }

    private def inferReturnType(context: EValueContext, eparams: Seq[EParameter]) = {
      val paramVariables = eparams.map(param => {
        val typ = param.pattern match {
          case Pattern.SpecificValue(value) => value.assertType
          case _ => throw EvalError("Cannot infer the return type of a function using type vals", param.location)
        }

        Variable(fn.nameMap(param.inName), $Unknown.Value(typ, param.location))
      })

      val partialScope = scope.newChild(paramVariables)
      val ebody = context.toEValue(fn.body, partialScope)

      ebody.realType
    }
  }
}

object $Function extends Type {
  val meta = new Type {
    override def typ: Type = $Type
    override def toUValue(core: Core): UValue = inconvertible
    override val methods = Map(
      // e.g. Function(a = Int, returns = Bool)
      // This should return an interface, not $FunctionT as we can't
      // handle patterns like this
      "call" -> new CompileTimeOnlyMethod {
        override val signature: MethodSignature = ???
        override def apply(args: CallSpec, location: Option[Location]): EValue = ???
      }
    )
  }

  override def typ = $Type
  override def toUValue(core: Core): UValue = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class EParameter(outName: String, inName: String, pattern: Pattern, location: Option[Location]) {
  def toUParameter(core: Core) = UParameter(outName, inName, pattern.toUPattern(core), location)
}

case class $FunctionT(
  params: Seq[EParameter],
  returnType: EValue,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference
) extends Type {
  override def typ: Type = $Function

  override def unboundNames: Set[VariableName] =
    params.flatMap(_.pattern.unboundNames).toSet ++ typ.unboundNames
  override def toUValue(core: Core): UValue = inconvertible
  override val methods = Map(
    "runTimeOnly" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature(
        Seq.empty,
        $FunctionT(params, returnType, FunctionRunMode.RunTimeOnly, inlinePreference)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$FunctionT]
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, fn.location)
      }
    },

    "compileTimeOnly" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature(
        Seq.empty,
        $FunctionT(params, returnType, FunctionRunMode.CompileTimeOnly, inlinePreference)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$FunctionT]
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, fn.location)
      }
    },

    "inline" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature(
        Seq.empty,
        $FunctionT(params, returnType, runMode, InlinePreference.ForceInline)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$FunctionT]
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, fn.location)
      }
    },

    "noInline" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature(
        Seq.empty,
        $FunctionT(params, returnType, runMode, InlinePreference.NoInline)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$FunctionT]
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, fn.location)
      }
    },

    "call" -> new Method {
      override val signature = MethodSignature(
        params.map(param => param.outName -> param.pattern),
        returnType
      )

      override def call(args: CallSpec, location: Option[Location]) = {
        val evalRequest = this.evalRequest(EValue.context.evalMode)

        evalRequest match {
          case FunctionEvalRequest.ForceEval =>
            // We should execute the function fully, nothing left for later
            val context = EValue.context.copy(
              evalMode = forcedEvalMode(EValue.context.evalMode)
            )

            EValue.withContext(context) {
              val fn = args.selfEval[FunctionValue]
              val self = PartialValue(fn, Seq.empty)

              // TODO: To avoid infinite recursion, this should check that the args can be fully evaluated first
              val bodyWrappedInLets = buildCodeForExecution(fn, self, args, location)

              val result = bodyWrappedInLets.evaluated
              if (result.isOperation) {
                throw EvalError(s"Could not fully evaluate function $fn", location)
              }

              result
            }

          case FunctionEvalRequest.InlineIfPossible |
               FunctionEvalRequest.InlineIfDefinedInline =>
            val partialSelf = args.self.evaluated.partialValue(
              followReferences = evalRequest == FunctionEvalRequest.InlineIfPossible
            )
            val fn = partialSelf.value match {
              case fn: FunctionValue => fn
              case value if value.isOperation => throw DelayCall
              case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
            }

            val bodyWrappedInLets = buildCodeForExecution(fn, partialSelf, args, location)
            bodyWrappedInLets.evaluated
        }
      }

      private def forcedEvalMode(evalMode: EvalMode) = evalMode match {
        case EvalMode.RunTime => EvalMode.RunTime
        case EvalMode.CompileTimeOnly |
             EvalMode.Partial |
             EvalMode.PartialRunTimeOnly |
             EvalMode.PartialPreferRunTime => EvalMode.CompileTimeOnly
      }

      private def evalRequest(evalMode: EvalMode) = evalMode match {
        case EvalMode.RunTime => runMode match {
          case FunctionRunMode.CompileTimeOnly => throw CannotCallCompileTimeMethodInRunTimeMethod
          case FunctionRunMode.Default => FunctionEvalRequest.ForceEval
          case FunctionRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
          case FunctionRunMode.RunTimeOnly => FunctionEvalRequest.ForceEval
        }

        case EvalMode.CompileTimeOnly => runMode match {
          case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case FunctionRunMode.Default => FunctionEvalRequest.ForceEval
          case FunctionRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
          case FunctionRunMode.RunTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
        }

        case EvalMode.Partial => runMode match {
          case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case FunctionRunMode.Default =>
            inlinePreference match {
              case InlinePreference.ForceInline => FunctionEvalRequest.InlineIfPossible
              case InlinePreference.Default => FunctionEvalRequest.InlineIfDefinedInline
              case InlinePreference.NoInline => throw DelayCall
            }

          case FunctionRunMode.PreferRunTime => throw DelayCall
          case FunctionRunMode.RunTimeOnly => throw DelayCall
        }

        case EvalMode.PartialRunTimeOnly |
             EvalMode.PartialPreferRunTime => runMode match {
          case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case FunctionRunMode.Default => throw DelayCall
          case FunctionRunMode.PreferRunTime => throw DelayCall
          case FunctionRunMode.RunTimeOnly => throw DelayCall
        }
      }

      private def buildCodeForExecution(
        fn: FunctionValue,
        self: PartialValue,
        args: CallSpec,
        location: Option[Location]
      ): EValue = ???
    }
  )
}

case class FunctionValue(
  typ: $FunctionT,
  nameMap: Map[String, VariableName],
  body: UValue,
  scope: Scope,
  location: Option[Location]
) extends RealEValue {
  override def unboundNames = body.unboundNames ++ typ.unboundNames -- nameMap.values

  // TODO: Encode method traits with `.compileTimeOnly` / `.runTimeOnly`
  //       and inline preference as well.
  override def toUValue(core: Core) =
    UOperation.Function(
      new UFunction(
        typ.params.map(_.toUParameter(core)),
        nameMap,
        body,
        Some(typ.returnType.toUValue(core))
      ),
      location
    )
}
