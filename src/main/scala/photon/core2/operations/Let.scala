package photon.core2.operations

import photon.core2.{StandardType, TypeRoot}
import photon.{EValue, Location, VariableName}

object Let extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

case class LetValue(name: VariableName, value: EValue, body: EValue, location: Option[Location]) extends EValue {
  override val typ = Block
}
