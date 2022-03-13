package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, UOperation, Variable, VariableName}

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

  def partialValue: PartialValue = partialValue(Seq.newBuilder)

  @tailrec
  private def partialValue(variables: mutable.Builder[Variable, Seq[Variable]]): PartialValue = {
    variables.addOne(Variable(name, value))

    body match {
      case let: LetValue => let.partialValue(variables)
      case _ => PartialValue(body, variables.result)
    }
  }
}

case class PartialValue(value: EValue, variables: Seq[Variable]) {
  def replaceWith(newValue: EValue) = PartialValue(newValue, variables)
  def addOuterVariables(additionalVars: Seq[Variable]) = PartialValue(value, additionalVars ++ variables)
  def addInnerVariables(additionalVars: Seq[Variable]) = PartialValue(value, variables ++ additionalVars)

  def wrapBack: EValue =
    variables.foldRight(value) { case (Variable(name, varValue), innerValue) =>
      if (innerValue.unboundNames.contains(name)) {
        LetValue(name, varValue, innerValue, varValue.location)
      } else {
        innerValue
      }
    }
}