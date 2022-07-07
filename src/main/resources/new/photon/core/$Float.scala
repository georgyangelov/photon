package photon.core

import photon.Core
import photon.base._
import photon.frontend.{ULiteral, UValue}

object $Float extends Type {
  val meta = new Type {
    override def typ = $Type
    override def toUValue(core: Core): UValue = inconvertible
    override val methods = Map(

    )
  }

  override def typ = meta
  override def toUValue(core: Core): UValue = core.referenceTo(this, location)
  override val methods = Map(

  )

  case class Value(value: Double, location: Option[Location]) extends RealEValue {
    override def typ = $Float
    override def unboundNames = Set.empty
    override def toUValue(core: Core): UValue = ULiteral.Float(value, location)
  }
}
