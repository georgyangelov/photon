package photon.core.objects

import photon.base.{Method, Scope, Type}
import photon.core.$Type

object $Int extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
//    "+" -> new Method {
//
//    }
  )
}
