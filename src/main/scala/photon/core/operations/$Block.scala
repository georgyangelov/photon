package photon.core.operations

import photon.Core
import photon.base._
import photon.core._
import photon.lib.ScalaExtensions._
import photon.frontend._

object $Block extends Type {
  override def typ = $Type
  override def toUValue(core: Core): UValue = inconvertible
  override val methods = Map.empty

  case class Value(values: Seq[EValue], location: Option[Location]) extends EValue {
    override def typ: Type = $Block
    override def isOperation = true
    override def unboundNames = values.map(_.unboundNames).unionSets
    override def toUValue(core: Core) = UOperation.Block(values.map(_.toUValue(core)), location)
    override def evalMayHaveSideEffects = values.exists(_.evalMayHaveSideEffects)

    override def realType =
      if (values.nonEmpty)
        values.last.realType
      else
        // TODO: Support empty blocks?
        throw EvalError("Empty blocks are not supported for now", location)

    override def evaluate(mode: EvalMode): EValue = {
      val lastValueIndex = values.length - 1
      val evalues = values
        .map(_.evaluated)
        .zipWithIndex
        .filter { case (value, index) => index == lastValueIndex || value.evalMayHaveSideEffects }
        .map(_._1)

      if (evalues.length == 1) {
        evalues.head
      } else {
        $Block.Value(evalues, location)
      }
    }
  }
}