package photon.core

import photon.Core
import photon.base._
import photon.frontend.UValue

object $Type extends Type {
  override def typ: Type = this
  override val methods = Map.empty
  override def toUValue(core: Core): UValue = core.referenceTo(this, location)
}