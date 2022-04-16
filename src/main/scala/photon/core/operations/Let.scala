package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, PartialValue, UOperation, Variable, VariableName}

import scala.annotation.tailrec
import scala.collection.mutable

object Let extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class LetValue(name: VariableName, value: EValue, body: EValue, location: Option[Location]) extends EValue {
  override def isOperation = true
  override val typ = Block
  override def unboundNames = value.unboundNames ++ body.unboundNames - name
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects || body.evalMayHaveSideEffects
  override def evalType = Some(body.evalType.getOrElse(body.typ))

  override protected def evaluate: EValue = {
    val ENABLE_SIDE_EFFECT_CHECK = true

    val evalue = value.evaluated
    val ebody = body.evaluated

    // Inline if the body is a direct reference to this let value
    ebody match {
      case ref: ReferenceValue if ref.variable.name == name => evalue
      case _ if ebody.unboundNames.contains(name) => LetValue(name, evalue, ebody, location)
      case _ if !ENABLE_SIDE_EFFECT_CHECK || evalue.evalMayHaveSideEffects =>
        BlockValue(Seq(evalue, ebody), location)
      case _ => ebody
    }
  }

  override def finalEval = {
    val ENABLE_SIDE_EFFECT_CHECK = true

    val evalue = value.finalEval
    val ebody = body.finalEval

    if (ebody.unboundNames.contains(name)) {
      LetValue(name, evalue, ebody, location)
    } else if (!ENABLE_SIDE_EFFECT_CHECK) {
      BlockValue(Seq(evalue, ebody), location)
    } else if (evalue.evalMayHaveSideEffects) {
      BlockValue(Seq(evalue, ebody), location)
    } else {
      ebody
    }
  }

  override def toUValue(core: Core) = UOperation.Let(name, value.toUValue(core), body.toUValue(core), location)

  override def partialValue(followReferences: Boolean): PartialValue =
    body.partialValue(followReferences).withOuterVariable(Variable(name, value))
//    body match {
//      case reference: ReferenceValue => reference.partialValue(followReferences)
//      case _ => body.partialValue(followReferences).withOuterVariable(Variable(name, value))
//    }
}