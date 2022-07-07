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

object $Function {
  def of(fn: UOperation.Function): $Function.Value = {
    val signature = MethodSignature.of(
      args = fn.fn.params.map(param => param.outName -> param.typ),
      fn.fn.returnType.getOrElse {
        // TODO: Infer return types for non-generic functions
        throw EvalError("Inferring function return types is not yet supported", fn.location)
      }
    )
    val typ = $Function(signature, FunctionRunMode.Default, InlinePreference.Default)

    Value(typ, fn.fn, fn.location)
  }

  case class Value(typ: $Function, fn: UFunction, location: Option[Location]) extends RealEValue {
    override def unboundNames = fn.unboundNames

    // TODO: Encode method traits with `.compileTimeOnly` / `.runTimeOnly`
    //       and inline preference as well.
    override def toUValue(core: Core) = UOperation.Function(fn, location)
  }
}

case class $Function(
  signature: MethodSignature,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference
) extends Type {
  override def typ: Type = $Type
  override def toUValue(core: Core) = inconvertible

  val self = this

  override val methods = Map(
    "runTimeOnly" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(
        Seq.empty,
        $Function(self.signature, FunctionRunMode.RunTimeOnly, inlinePreference)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$Function]
        val fn = args.selfEval[$Function.Value].fn

        $Function.Value(newType, fn, location)
      }
    },

    "compileTimeOnly" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(
        Seq.empty,
        $Function(self.signature, FunctionRunMode.CompileTimeOnly, inlinePreference)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$Function]
        val fn = args.selfEval[$Function.Value].fn

        $Function.Value(newType, fn, location)
      }
    },

    "inline" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(
        Seq.empty,
        $Function(self.signature, runMode, InlinePreference.ForceInline)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$Function]
        val fn = args.selfEval[$Function.Value].fn

        $Function.Value(newType, fn, location)
      }
    },

    "noInline" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(
        Seq.empty,
        $Function(self.signature, runMode, InlinePreference.NoInline)
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val newType = args.returnType.assertSpecificType[$Function]
        val fn = args.selfEval[$Function.Value].fn

        $Function.Value(newType, fn, location)
      }
    },

    "call" -> new Method {
      override val signature = self.signature

      override def call(args: CallSpec, location: Option[Location]) = {
        val evalRequest = this.evalRequest(EValue.context.evalMode)

        evalRequest match {
          case FunctionEvalRequest.ForceEval =>
            // We should execute the function fully, nothing left for later
            val context = EValue.context.copy(
              evalMode = forcedEvalMode(EValue.context.evalMode)
            )

            EValue.withContext(context) {
              val fn = args.selfEval[$Function.Value]
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
              case fn: $Function.Value => fn
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
        fn: $Function.Value,
        self: PartialValue,
        args: CallSpec,
        location: Option[Location]
      ): EValue = {
        
      }
    }
  )
}
