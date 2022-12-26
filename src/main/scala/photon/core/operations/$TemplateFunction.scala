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
      override val signature = MethodSignature.Any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(templateClosure, templateClosureClosures) = spec.requireSelf[TemplateClosure](env)

        val argTypes = spec.args
          .withoutSelf
          .map(_.typ(env.scope))

        // TODO: Calculate this once - move to the TemplateClosure class
//        val innerSignature = MethodSignature.Template(
//          templateClosure.scope,
//          templateClosure.fnDef.params.map { param => param.outName -> param.pattern },
//          templateClosure.fnDef.returnType
//            // TODO: This can be inferred after the parameters are specialized
//            .getOrElse { throw EvalError("Template function needs to have a return type", templateClosure.location) }
//        )

        val concreteSignature = templateClosure.signature.instantiate(argTypes) match {
          case Left(concreteSignature) => concreteSignature
          case Right(typeError) => throw typeError
        }

        val argsWithTypes = spec.args.map { value => value -> value.typ(env.scope) }
        val callSpec = concreteSignature.specialize(argsWithTypes) match {
          case Left(callSpec) => callSpec
          case Right(typeError) => throw typeError
        }

        val paramTypes = callSpec.args
          .map(_.typ(env.scope))
          .withoutSelf
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
      override val signature = MethodSignature.Any($AnyStatic)

      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(closure, closureClosures) = spec.requireSelf[TemplateClosure](env)
        val types = spec.args.map(_.asType).positional

        val concreteSignature = closure.signature.instantiate(
          ArgumentsWithoutSelf.positional(types)
        ) match {
          case Left(concreteSignature) => concreteSignature
          case Right(typeError) => throw typeError
        }

        val returnType = concreteSignature.returnType

        val result = instantiate(closure, types, Some(returnType)).evaluate(env)

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

  val signature = MethodSignature.Template(
    scope,
    fnDef.params.map { param => param.outName -> param.pattern },
    fnDef.returnType
      // TODO: This can be inferred after the parameters are specialized
      .getOrElse { throw EvalError("Template function needs to have a return type", location) }
  )

  // TODO: Figure this out
  override def evaluate(env: Environment) = EvalResult(this, Seq.empty)

  override def toAST(names: Map[VarName, String]) = fnDef.toAST(names)
}