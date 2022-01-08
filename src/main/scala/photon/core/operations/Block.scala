package photon.core.operations

import photon.core.{Core, StandardType, TypeRoot}
import photon.{EValue, Location, UOperation}

object Block extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class BlockValue(values: Seq[EValue], location: Option[Location]) extends EValue {
  override val typ = Block
  override def unboundNames = values.flatMap(_.unboundNames).toSet
  override def evalMayHaveSideEffects = values.exists(_.evalMayHaveSideEffects)

  override def evalType =
//    if (values.nonEmpty)
      Some(values.last.evalType.getOrElse(values.last.typ))
//    else
//      NothingValue(location)

  override protected def evaluate: EValue = {
    val ENABLE_SIDE_EFFECT_CHECK = true

    val lastValueIndex = values.length - 1
    val evalues = values
      .map(_.evaluated)
      .zipWithIndex
      .filter { case (value, index) => !ENABLE_SIDE_EFFECT_CHECK || index == lastValueIndex || value.evalMayHaveSideEffects }
      .map(_._1)

    if (evalues.length == 1) {
      evalues.head
    } else {
      BlockValue(evalues, location)
    }
  }

  override def toUValue(core: Core) = UOperation.Block(values.map(_.toUValue(core)), location)
}
