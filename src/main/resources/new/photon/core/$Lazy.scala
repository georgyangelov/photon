package photon.core

import photon.Core
import photon.base.{EValue, EvalError, EvalMode, Location}
import photon.lib.Lazy

// TODO: Make this a Type?
object $Lazy {
  case class Value(value: Lazy[EValue], location: Option[Location]) extends EValue {
    override def unboundNames = value.resolve.unboundNames
    override def toUValue(core: Core) = value.resolve.toUValue(core)
    override def typ = value.resolve.typ
    override def realType = value.resolve.realType

    override def evaluated = value.resolve.evaluated
    override def evaluated(mode: EvalMode) = value.resolve.evaluated(mode)
    override def partialValue(followReferences: Boolean) = value.resolve.partialValue(followReferences)

    override def evaluate(mode: EvalMode) = throw EvalError("This should not happen", location)
  }
}