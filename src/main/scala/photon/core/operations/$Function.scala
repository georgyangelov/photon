package photon.core.operations

import photon.base._
import photon.core._

case class Parameter(
  outName: String,
  inName: VarName,
  typ: Value,
  location: Option[Location]
)

case class $FunctionDef(
  params: Seq[Parameter],
  body: Value,
  returnType: Value,
  location: Option[Location]
) extends Value {
  override def isOperation = true
  override def unboundNames: Set[VarName] =
    body.unboundNames -- params.map(_.inName) ++ params.flatMap(_.typ.unboundNames) ++ returnType.unboundNames

  // TODO: Cache this and share with `evaluate`?
  override def typ(scope: Scope) = {
    // TODO: Pattern types
    val signature = MethodSignature.of(
      args = params
        .map { param => param.outName -> param.typ.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).assertType },
      returnType.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).assertType
    )

    $Function(signature, FunctionRunMode.Default, InlinePreference.Default)
  }

  override def evaluate(env: Environment) =
    $Object(
      Closure(
        env.scope,
        names = params.map { param => param.inName.originalName -> param.inName }.toMap,
        body
      ),
      typ(env.scope),
      location
    )

  override def toAST(names: Map[VarName, String]) = ???
}

case class Closure(scope: Scope, names: Map[String, VarName], body: Value)

case class $Function(
  signature: MethodSignature,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference
) extends Type {
  val self = this

  override def typ(scope: Scope) = $Type
  override val methods = Map(
    "call" -> new Method {
      override val signature = self.signature

      override def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val evalRequest = this.evalRequest(env.evalMode)

        evalRequest match {
          case FunctionEvalRequest.ForceEval =>
            // We should execute the function fully, nothing left for later
            val execEnv = Environment(
              env.scope,
              forcedEvalMode(env.evalMode)
            )

            val closure = spec.requireSelfObject[Closure](execEnv)

            val execBody = buildBodyForExecution(closure, spec)

            execBody.evaluate(execEnv).wrapInLets

          case FunctionEvalRequest.InlineIfPossible |
               FunctionEvalRequest.InlineIfDefinedInline =>
            val partialSelf = spec.requireSelf[Value](env).partialValue(
              env,
              followReferences = evalRequest == FunctionEvalRequest.InlineIfPossible
            )

            val closure = partialSelf.value match {
              case $Object(closure: Closure, _, _) => closure
              case value if value.isOperation => throw DelayCall
              case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
            }

            val execBody = buildBodyForExecution(closure, spec)

            execBody.evaluate(env).wrapInLets
        }
      }

      private def buildBodyForExecution(closure: Closure, spec: CallSpec): PartialValue = {
        val variables = spec.bindings.map { case name -> value => closure.names(name) -> value }

        PartialValue(closure.body, variables)
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

      private def forcedEvalMode(evalMode: EvalMode) = evalMode match {
        case EvalMode.RunTime => EvalMode.RunTime
        case EvalMode.CompileTimeOnly |
             EvalMode.Partial |
             EvalMode.PartialRunTimeOnly |
             EvalMode.PartialPreferRunTime => EvalMode.CompileTimeOnly
      }
    }
  )
}

sealed trait FunctionRunMode
object FunctionRunMode {
  case object CompileTimeOnly extends FunctionRunMode
  case object Default         extends FunctionRunMode
  case object PreferRunTime   extends FunctionRunMode
  case object RunTimeOnly     extends FunctionRunMode
}

sealed trait InlinePreference
object InlinePreference {
  case object ForceInline extends InlinePreference
  case object Default     extends InlinePreference
  case object NoInline    extends InlinePreference
}

sealed trait FunctionEvalRequest
object FunctionEvalRequest {
  case object ForceEval extends FunctionEvalRequest
  case object InlineIfPossible extends FunctionEvalRequest
  case object InlineIfDefinedInline extends FunctionEvalRequest
}
