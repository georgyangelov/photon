package photon.core.operations

import photon.Core
import photon.base._
import photon.core._
import photon.frontend._

object $Reference extends Type {
  override def typ = $Type
  override def toUValue(core: Core): UValue = inconvertible
  override val methods = Map.empty

  case class Value(name: VariableName, value: EValue, location: Option[Location]) extends EValue {
    override def typ: Type = $Reference
    override def isOperation = true
    override def unboundNames = Set(name)
    override def toUValue(core: Core) = UOperation.Reference(name, location)
    override def realType = value.realType

    override def evaluate(mode: EvalMode) =
      EValue.context.evalMode match {
        case EvalMode.CompileTimeOnly |
             EvalMode.RunTime => value.evaluated

        // If it's in a partial mode => it's not required (yet)
        case EvalMode.Partial |
             EvalMode.PartialPreferRunTime |
             EvalMode.PartialRunTimeOnly => this
      }

    override def partialValue(followReferences: Boolean) =
      if (followReferences) {
        value.evaluated.partialValue(followReferences)
      } else {
        PartialValue(this, Seq.empty)
      }
  }
}