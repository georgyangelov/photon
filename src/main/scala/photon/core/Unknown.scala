package photon.core

import photon.interpreter.EvalError
import photon.{EValue, Location}

object Unknown extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class UnknownValue(etype: Type, location: Option[Location]) extends EValue {
  override def typ = Unknown
  override def evalMayHaveSideEffects = false
  override def evalType = Some(etype)
  override def toUValue(core: Core) = inconvertible
  override def evaluate = throw EvalError("Cannot evaluate Unknown", location)
}