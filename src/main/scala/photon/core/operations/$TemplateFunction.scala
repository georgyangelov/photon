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
    TemplateClosure(env.scope, this)

  // TODO: Implement this
  override def toAST(names: Map[VarName, String]) = ???
}

object $TemplateFunction extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    "call" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        val templateClosure = spec.requireSelf[TemplateClosure](env)

        // TODO: Calculate this once
        val innerSignature = MethodSignature.ofPatterns(
          templateClosure.scope,
          templateClosure.fnDef.params.map { param => param.outName -> param.pattern },
          templateClosure.fnDef.returnType
            // TODO: This can be inferred after the parameters are specialized
            .getOrElse { throw EvalError("Template function needs to have a return type", templateClosure.location) }
        )

        // TODO: Check for type errors
        val callSpec = innerSignature.specialize(spec.args, env.scope)

        // TODO: Instantiate specific function (create $FunctionDef, then evaluate it (may not need to because $Call should evaluate it))
        // TODO: $Call that $FunctionDef and .evaluate it

        $Call(
          "call"
        )
      }
    }
  )
}

case class TemplateClosure(scope: Scope, fnDef: $TemplateFunctionDef) extends Value {
  override def evalMayHaveSideEffects = false
  override def location = fnDef.location
  override def unboundNames = fnDef.unboundNames

  override def typ(scope: Scope) = $TemplateFunction

  override def evaluate(env: Environment) = ???

  override def toAST(names: Map[VarName, String]) = fnDef.toAST(names)
}