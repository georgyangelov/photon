package photon.core.objects

import photon.base._
import photon.core._

object $NativeHandle extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods = Map()
}