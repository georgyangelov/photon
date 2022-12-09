package photon.core.objects

import photon.base._
import photon.core._

object $Internal extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "array_new" -> new DefaultMethod {
      override val signature = MethodSignature.any($NativeHandle)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val items = spec.requireAllConcretePositional(env)

        // items.mapValue(Array(_))
        ???
      }
    }
  )
}
