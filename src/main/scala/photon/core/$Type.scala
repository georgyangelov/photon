package photon.core

import photon.base._
import photon.lib.Lazy

object $Type extends Type {
  override def typ(scope: Scope): Type = this
  override val methods: Map[String, Method] = Map.empty
}

case class $LazyType(value: Lazy[Type]) extends Type {
  override def typ(scope: Scope): Type = value.resolve.typ(scope)
  override lazy val methods: Map[String, Method] = value.resolve.methods
}