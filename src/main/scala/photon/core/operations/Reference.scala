package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, EvalMode, Location, UOperation, Variable}

object Reference extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override val methods = Map.empty
  override def toUValue(core: Core) = inconvertible
}

case class ReferenceValue(variable: Variable, location: Option[Location]) extends EValue {
  override def isOperation = true
  override val typ = Reference
  override def unboundNames = Set(variable.name)
  override def evalMayHaveSideEffects = false
  override def evalType = Some(variable.value.evalType.getOrElse(variable.value.typ))
  override def toUValue(core: Core) = UOperation.Reference(variable.name, location)

  // TODO: Cache result of this based on evalMode
  override protected def evaluate: EValue =
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime => variable.value.evaluated

      // If it's in a partial mode => it's not required (yet)
      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly => this
    }

  override def finalEval = this
}
