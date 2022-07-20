package photon.core.objects

import photon.base._
import photon.core.$Type

object $AnyStatic extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map.empty
}
