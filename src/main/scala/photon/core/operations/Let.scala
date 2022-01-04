package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, UOperation, VariableName}

object Let extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class LetValue(name: VariableName, value: EValue, body: EValue, location: Option[Location]) extends EValue {
  override val typ = Block
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects || body.evalMayHaveSideEffects
  override def evalType = Some(body.evalType.getOrElse(body.typ))

  override protected def evaluate: EValue = {
    val evalue = value.evaluated
    val ebody = body.evaluated

    if (evalue.evalMayHaveSideEffects) {
      BlockValue(Seq(evalue, ebody), location)
    } else {
      ebody.evaluated
    }
  }

  override def toUValue(core: Core) = UOperation.Let(name, value.toUValue(core), body.toUValue(core), location)
}