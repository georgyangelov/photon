package photon.core.operations

import photon.Core
import photon.base._
import photon.core._
import photon.frontend._

object $Let extends Type {
  override def typ = $Type
  override def toUValue(core: Core): UValue = inconvertible
  override val methods = Map.empty

  case class Value(name: VariableName, value: EValue, body: EValue, location: Option[Location]) extends EValue {
    override def typ: Type = $Let
    override def isOperation = true
    override def unboundNames = value.unboundNames ++ body.unboundNames - name
    override def evalMayHaveSideEffects = value.evalMayHaveSideEffects || body.evalMayHaveSideEffects
    override def toUValue(core: Core) = UOperation.Let(name, value.toUValue(core), body.toUValue(core), location)
    override def realType = body.realType

    override def evaluate(mode: EvalMode): EValue = {
      val evalue = value.evaluated
      val ebody = body.evaluated

      ebody match {
        // Inline if the body is a direct reference to this let value
        case ref: $Reference.Value if ref.name == name => evalue
        case _ if ebody.unboundNames.contains(name) => $Let.Value(name, evalue, ebody, location)
        case _ if evalue.evalMayHaveSideEffects => $Block.Value(Seq(evalue, ebody), location)
        case _ => ebody
      }
    }

    override def partialValue(followReferences: Boolean) =
      body.partialValue(followReferences).withOuterVariable(Variable(name, value))
  }
}