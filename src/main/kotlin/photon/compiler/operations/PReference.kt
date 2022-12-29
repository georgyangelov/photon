package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.*
import photon.core.Location

class PReference(
  val name: String,
  private val isArgument: Boolean,
  private val slot: Int,
  val location: Location?
): Value() {
  override fun isOperation(): Boolean = true

  override fun typeOf(frame: VirtualFrame): Type {
    val value = frame.getValue(slot)

    if (value is Value) {
      return value.typeOf(frame)
    } else {
      throw RuntimeException("Cannot get type of non-Value object")
    }
  }

  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any {
    // TODO: Respect EvalMode
    return if (isArgument) {
      frame.arguments[slot]
    } else {
      frame.getObject(slot)
    }
  }
}