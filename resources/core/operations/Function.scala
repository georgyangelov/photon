package photon.core.operations

import photon.ArgumentExtensions._
import photon.core.{Core, StandardType, Type, TypeRoot, UnknownValue}
import photon.interpreter.{EvalError, URename}
import photon.{Arguments, CannotCallCompileTimeMethodInRunTimeMethod, CannotCallRunTimeMethodInCompileTimeMethod, CompileTimeOnlyMethod, DelayCall, EValue, EValueContext, EvalMode, InlinePreference, Location, Method, MethodRunMode, MethodType, PartialValue, Scope, UFunction, UOperation, UParameter, UValue, Variable, VariableName}

object FunctionDef extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class FunctionDefValue(fn: photon.UFunction, scope: Scope, location: Option[Location]) extends EValue {
  override def isOperation = true
  override val typ = FunctionDef
  override def unboundNames = fn.unboundNames

  override def evalMayHaveSideEffects = false
  override def evalType = Some(evaluated.typ)

  // TODO: Should this indirection be here at all?
  //       Maybe when type inference for parameters is implemented?
  override protected def evaluate: EValue = {
    val context = EValue.context
    val eParams = fn.params.map { param =>
      val argType = context.toEValue(param.typ, scope)

      EParameter(param.name, argType, location)
    }

    val eReturnType = fn.returnType
      .map(context.toEValue(_, scope))
      .getOrElse { inferReturnType(context, eParams) }

    val functionType = FunctionT(eParams, eReturnType, MethodRunMode.Default, InlinePreference.Default)

    FunctionValue(functionType, fn.nameMap, fn.body, scope, location)
  }

  override def finalEval = evaluated.finalEval

  override def toUValue(core: Core) = UOperation.Function(fn, location)

  private def inferReturnType(context: EValueContext, eparams: Seq[EParameter]) = {
    val partialScope = scope.newChild(eparams.map { param =>
      Variable(fn.nameMap(param.name), UnknownValue(param.typ.assertType, param.location))
    })
    val ebody = context.toEValue(fn.body, partialScope)

    ebody.evalType.getOrElse(ebody.typ)
  }
}

object FunctionRootType extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "call" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        if (args.positional.nonEmpty) {
          throw EvalError("Function type parameters must be named", location)
        }

        MethodType.of(
          args.named.toSeq.map { case (name, _) => name -> TypeRoot },
          FunctionRoot
        )
      }

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val returnType = args.named.getOrElse(
          "returns",
          throw EvalError("Function type must have a `returns` argument", location)
        )
        val params = (args.named - "returns")
          .map { case name -> etype => EParameter(name, etype, etype.location) }
          .toSeq

        // TODO: This should actually return an interface and should only have a runMode if it has a known object inside
        FunctionT(params, returnType, MethodRunMode.Default, InlinePreference.Default)
      }
    }
  )
}

object FunctionRoot extends StandardType {
  override def typ = FunctionRootType

  // TODO: These should probably reference the core.referenceTo(this, ...)
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

sealed trait FunctionEvalRequest
object FunctionEvalRequest {
  case object ForceEval extends FunctionEvalRequest
  case object InlineIfPossible extends FunctionEvalRequest
  case object InlineIfDefinedInline extends FunctionEvalRequest
}

case class FunctionT(
  params: Seq[EParameter],
  returnType: EValue,
  runMode: MethodRunMode,
  inlinePreference: InlinePreference
) extends StandardType {
  override def typ = FunctionRoot
  override val location = None

  override def finalEval = FunctionT(
    params.map(p => EParameter(p.name, p.typ.finalEval, p.location)),
    returnType.finalEval,
    runMode,
    inlinePreference
  )

  override val methods = Map(
    "returnType" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, TypeRoot)

      override def run(args: Arguments[EValue], location: Option[Location]) = returnType
    },

    "runTimeOnly" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq.empty,
          FunctionT(params, returnType, MethodRunMode.RunTimeOnly, inlinePreference)
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, MethodRunMode.RunTimeOnly, inlinePreference)
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "compileTimeOnly" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq.empty,
          FunctionT(params, returnType, MethodRunMode.CompileTimeOnly, inlinePreference)
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, MethodRunMode.CompileTimeOnly, inlinePreference)
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "inline" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq.empty,
          FunctionT(params, returnType, runMode, InlinePreference.ForceInline)
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, runMode, InlinePreference.ForceInline)
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "noInline" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq.empty,
          FunctionT(params, returnType, runMode, InlinePreference.NoInline)
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, runMode, InlinePreference.NoInline)
        val fn = args.selfEval[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "call" -> new Method {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          params.map(param => param.name -> param.typ),
          returnType.assertType
        )

      private def evalRequest(evalMode: EvalMode) = evalMode match {
        case EvalMode.RunTime => runMode match {
          case MethodRunMode.CompileTimeOnly => throw CannotCallCompileTimeMethodInRunTimeMethod
          case MethodRunMode.Default => FunctionEvalRequest.ForceEval
          case MethodRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
          case MethodRunMode.RunTimeOnly => FunctionEvalRequest.ForceEval
        }

        case EvalMode.CompileTimeOnly => runMode match {
          case MethodRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case MethodRunMode.Default => FunctionEvalRequest.ForceEval
          case MethodRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
          case MethodRunMode.RunTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
        }

        case EvalMode.Partial => runMode match {
          case MethodRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case MethodRunMode.Default =>
            inlinePreference match {
              case InlinePreference.ForceInline => FunctionEvalRequest.InlineIfPossible
              case InlinePreference.Default => FunctionEvalRequest.InlineIfDefinedInline
              case InlinePreference.NoInline => throw DelayCall
            }

          case MethodRunMode.PreferRunTime => throw DelayCall
          case MethodRunMode.RunTimeOnly => throw DelayCall
        }

        case EvalMode.PartialRunTimeOnly |
             EvalMode.PartialPreferRunTime => runMode match {
          case MethodRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
          case MethodRunMode.Default => throw DelayCall
          case MethodRunMode.PreferRunTime => throw DelayCall
          case MethodRunMode.RunTimeOnly => throw DelayCall
        }
      }

      private def forcedEvalMode(evalMode: EvalMode) = evalMode match {
        case EvalMode.RunTime => EvalMode.RunTime
        case EvalMode.CompileTimeOnly |
             EvalMode.Partial |
             EvalMode.PartialRunTimeOnly |
             EvalMode.PartialPreferRunTime => EvalMode.CompileTimeOnly
      }

      override def call(args: Arguments[EValue], location: Option[Location]): EValue = {
        val evalRequest = this.evalRequest(EValue.context.evalMode)

        evalRequest match {
          case FunctionEvalRequest.ForceEval =>
            // We should execute the function fully, nothing left for later

            val context = EValueContext(
              interpreter = EValue.context.interpreter,
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

          case _ =>
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

      private def buildCodeForExecution(
        fn: FunctionValue,
        self: PartialValue,
        args: Arguments[EValue],
        location: Option[Location]
      ) = {
        // TODO: Better arg check + do it in Call#evaluate
        if (args.count != params.size) {
          throw EvalError("Wrong number of arguments for this function", location)
        }

        val paramNames = params.map(_.name)
        val matchedArguments = args.matchWithNamesUnordered(paramNames)

        val localVariables = matchedArguments.map { case name -> value =>
          value match {
            case reference: ReferenceValue => (name, reference.variable, true)
            case _ => (name, Variable(new VariableName(name), value), false)
          }
        }

        val renames = localVariables
          .map { case (name, variable, _) => fn.nameMap(name) -> variable.name }
          .toMap

        val scope = fn.scope.newChild(localVariables.map(_._2))

        val renamedUBody = URename.rename(fn.body, renames)
        val ebody = EValue.context.toEValue(renamedUBody, scope)

        val bodyWrappedInLets = self
          .addInnerVariables(
            // TODO: Preserve order of definition so that latter variables can use prior ones
            localVariables
              .filter { case (_, _, isFromParentScope) => !isFromParentScope }
              .map(_._2)
          )
          .replaceWith(ebody)
          .wrapBack

        bodyWrappedInLets
      }
    }
  )

  // TODO: Add reference to FunctionRoot?
  override def unboundNames = params.flatMap(_.typ.unboundNames).toSet

  // TODO: Preserve order of definition so that latter variables can use prior ones
  override def toUValue(core: Core) =
    UOperation.Call(
      "call",
      Arguments.named(
        core.referenceTo(FunctionRoot, location),
        params.map { param => param.name -> param.typ.toUValue(core) }.toMap +
          ("returns" -> returnType.toUValue(core))
      ),
      location
    )
}

case class EParameter(name: String, typ: EValue, location: Option[Location]) {
  def toUParameter(core: Core) = UParameter(name, typ.toUValue(core), location)
}

case class FunctionValue(
  typ: FunctionT,
  nameMap: Map[String, VariableName],
  body: UValue,
  scope: Scope,
  location: Option[Location]
) extends EValue {
  override def unboundNames =
    typ.params.flatMap(_.typ.unboundNames).toSet ++
      typ.returnType.unboundNames ++
      body.unboundNames -- nameMap.values

  override def evalType = None
  override def evalMayHaveSideEffects = false
  override protected def evaluate: EValue = this

  override def finalEval: FunctionValue = {
    val partialScope = scope.newChild(typ.params.map { param =>
      Variable(nameMap(param.name), UnknownValue(param.typ.assertType, param.location))
    })

    val evalMode = typ.runMode match {
      // TODO: Do I need to partially evaluate this at all?
      case MethodRunMode.CompileTimeOnly => return this

      case MethodRunMode.Default => EvalMode.Partial
      case MethodRunMode.PreferRunTime => EvalMode.PartialPreferRunTime
      case MethodRunMode.RunTimeOnly => EvalMode.PartialRunTimeOnly
    }

    val partialContext = EValue.context.copy(evalMode = evalMode)
    val ebody = EValue.withContext(partialContext) {
      partialContext.toEValue(body, partialScope).evaluated.finalEval
    }
    val finalBody = ebody.toUValue(EValue.context.core)

    FunctionValue(typ, nameMap, finalBody, scope, location)
  }

  // TODO: Encode method traits with `.compileTimeOnly` / `.runTimeOnly`
  override def toUValue(core: Core) = UOperation.Function(
    new UFunction(
      typ.params.map(_.toUParameter(core)),
      nameMap,
      body,
      Some(typ.returnType.toUValue(core))
    ),
    location
  )
}
