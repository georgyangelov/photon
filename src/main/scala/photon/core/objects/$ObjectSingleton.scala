package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations._

object $ObjectSingleton extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Object.new
    "new" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.Any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val klass = $Call(
          "new",
          Arguments.positional(
            $Object(null, $Class, location),
            spec.args.positional
          ),
          location
        )

        $Call(
          "new",
          Arguments.empty(klass),
          location
        ).evaluate(env)
      }
    }
  )
}
