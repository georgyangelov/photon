package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, UOperation, Variable}

object Reference extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override val methods = Map.empty
  override def toUValue(core: Core) = inconvertible
}

case class ReferenceValue(variable: Variable, location: Option[Location]) extends EValue {
  override val typ = Reference
  override def unboundNames = Set(variable.name)
  override def evalMayHaveSideEffects = false
  override def evalType = Some(variable.value.evalType.getOrElse(variable.value.typ))
  override def toUValue(core: Core) = UOperation.Reference(variable.name, location)
  override protected def evaluate: EValue = variable.value.evaluated
}
