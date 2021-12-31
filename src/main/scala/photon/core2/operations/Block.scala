package photon.core2.operations

import photon.core2.{StandardType, TypeRoot}
import photon.{EValue, Location}

object Block extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

case class BlockValue(values: Seq[EValue], location: Option[Location]) extends EValue {
  override val typ = Block
}
