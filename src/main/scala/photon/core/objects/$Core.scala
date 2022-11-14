package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations.$Call

object $Core extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "typeCheck" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        typeCheck(env, spec, location)
      }
    }
  )

  private def typeCheck(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
    val value = spec.args.positional.head
    val valueType = value.typ(env.scope)

    // The type must be known at compile-time
    val requiredType = spec.args.positional(1).evaluate(env).asType

    // TODO: Type-check

//    val fromMethod = requiredType.typ(env.scope).method("from")
//      .getOrElse { throw EvalError(s"Cannot convert from $valueType to $requiredType because no `from` method exists", location) }

    val partialEnv = Environment(
      scope = env.scope,
      evalMode = EvalMode.Partial
    )

    $Call("from", Arguments.positional(requiredType, Seq(value)), location)
      .evaluate(partialEnv)
  }
}
