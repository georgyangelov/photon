package photon.core.operations

import photon.base._
import photon.core.$Type
import photon.core.objects.$AnyStatic

case class TemplateFunctionParameter(
  outName: String,
  inName: VarName,
  pattern: ValuePattern,
  location: Option[Location]
)

case class $TemplateFunctionDef(
  params: Seq[TemplateFunctionParameter],
  body: Value,
  returnType: Option[Value],
  location: Option[Location]
) extends Value {
  override def evalMayHaveSideEffects = false
  override def isOperation = true

  override def unboundNames = {
    val typeNames = ValuePattern.namesOfSequenceOfPatterns(params.map(_.pattern))

    body.unboundNames -- typeNames.defined -- params.map(_.inName) ++
      typeNames.unbound ++
      returnType.map(_.unboundNames).getOrElse(Set.empty)
  }

  override def typ(scope: Scope) = $TemplateFunction

  override def evaluate(env: Environment) =
    EvalResult(TemplateClosure(env.scope, this), Seq.empty)

  // TODO: Implement this
  override def toAST(names: Map[VarName, String]) = ???
}

object $TemplateFunction extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    "call" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(templateClosure, templateClosureClosures) = spec.requireSelf[TemplateClosure](env)

        // TODO: Calculate this once - move to the TemplateClosure class
        val innerSignature = MethodSignature.ofPatterns(
          templateClosure.scope,
          templateClosure.fnDef.params.map { param => param.outName -> param.pattern },
          templateClosure.fnDef.returnType
            // TODO: This can be inferred after the parameters are specialized
            .getOrElse { throw EvalError("Template function needs to have a return type", templateClosure.location) }
        )

        val callSpec = innerSignature.specialize(spec.args, env.scope) match {
          case Left(callSpec) => callSpec
          case Right(typeError) => throw typeError
        }

        val paramTypes = callSpec.args
          .map(_.typ(env.scope))
          .matchWith(templateClosure.fnDef.params.map { param => param.outName -> param.pattern })
          .map { case name -> (realType -> _) => name -> realType }.toMap

        val specificTypes = templateClosure.fnDef.params
          .map { case TemplateFunctionParameter(outName, _, _, _) => paramTypes(outName) }

        val fnInstance = instantiate(
          templateClosure,
          specificTypes,
          Some(callSpec.returnType)
        )

        val result = $Call(
          "call",
          callSpec.args.changeSelf(fnInstance),
          location
        ).evaluate(env)

        EvalResult(result.value, templateClosureClosures ++ result.closures)
      }
    },

    "instantiate" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)

      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(closure, closureClosures) = spec.requireSelf[TemplateClosure](env)
        val types = spec.args.map(_.asType).positional

        val innerSignature = MethodSignature.ofPatterns(
          closure.scope,
          closure.fnDef.params.map { param => param.outName -> param.pattern },
          closure.fnDef.returnType
            // TODO: This can be inferred after the parameters are specialized
            .getOrElse { throw EvalError("Template function needs to have a return type", closure.location) }
        )

        val returnType = innerSignature.specializeTypes(
          ArgumentsWithoutSelf.positional(types)
        ) match {
          case Some(returnType) => returnType
          case None => throw TypeError("Template function cannot be instantiated with these types", closure.location)
        }

        val result = instantiate(closure, types, returnType).evaluate(env)

        EvalResult(result.value, closureClosures ++ result.closures)
      }
    }
  )

  // TODO: Can we match this?
  def instantiate(
    closure: TemplateClosure,
    types: Seq[Type],
    returnType: Option[Value]
  ): Value = {
    val specificParams = closure.fnDef.params.zip(types)
      .map { case param -> typ => Parameter(param.outName, param.inName, typ, location) }

    $FunctionDef(
      specificParams,
      closure.fnDef.body,
      returnType,
      location
    )
  }
}

case class TemplateClosure(scope: Scope, fnDef: $TemplateFunctionDef) extends Value {
  override def evalMayHaveSideEffects = false
  override def location = fnDef.location
  override def unboundNames = fnDef.unboundNames

  override def typ(scope: Scope) = $TemplateFunction

  // TODO: Figure this out
  override def evaluate(env: Environment) = EvalResult(this, Seq.empty)

  override def toAST(names: Map[VarName, String]) = fnDef.toAST(names)
}