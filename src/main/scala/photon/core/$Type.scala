package photon.core

import photon.base._

object $Type extends Type {
  override def typ(scope: Scope): Type = this
  override val methods: Map[String, Method] = Map.empty
}
