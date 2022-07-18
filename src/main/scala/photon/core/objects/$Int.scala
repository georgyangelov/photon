package photon.core.objects

import photon.base._
import photon.core._

object $Int extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "+" -> new DefaultMethod {
      override val signature = MethodSignature.of(
        Seq("other" -> $Int),
        $Int
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val self = spec.requireSelfObject[Int](env)
        val other = spec.requireObject[Int](env, "other")

        $Object(self + other, $Int, location)
      }
    },

    "-" -> new DefaultMethod {
      override val signature = MethodSignature.of(
        Seq("other" -> $Int),
        $Int
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val self = spec.requireSelfObject[Int](env)
        val other = spec.requireObject[Int](env, "other")

        $Object(self - other, $Int, location)
      }
    },

    "*" -> new DefaultMethod {
      override val signature = MethodSignature.of(
        Seq("other" -> $Int),
        $Int
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val self = spec.requireSelfObject[Int](env)
        val other = spec.requireObject[Int](env, "other")

        $Object(self * other, $Int, location)
      }
    },
  )
}
