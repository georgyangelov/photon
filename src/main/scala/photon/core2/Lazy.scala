package photon.core2

import photon.lib.Lazy
import photon.{EValue, Location}

object Lazy extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

case class LazyValue(lazyValue: Lazy[EValue], location: Option[Location]) extends EValue {
  override def typ = lazyValue.resolve.typ
}
