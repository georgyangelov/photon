package photon.core

import photon.Core

object $Optional extends Type {
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override def typ = new Type {
    override def toUValue(core: Core) = inconvertible
    override def typ = $Type
    override val methods = Map(

    )
  }
  override val methods = Map.empty
}

case class $OptionalOf(t: Type)
