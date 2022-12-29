package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.*
import photon.core.Location

class PReference(
  val name: String,
  private val isArgument: Boolean,
  private val slot: Int,
  val location: Location?
): Operation() {
  override fun executePartial(frame: PartialFrame, evalMode: EvalMode): Value {
    val referencedType = if (isArgument) {
      frame.argumentTypes[slot]
    } else {
      frame.localTypes[slot]
    }

    if (referencedType == null) {
      throw RuntimeException("Cannot get type of reference, metadata does not contain slot $slot (isArgument = $isArgument). $frame")
    }

    type = referencedType

    // TODO: Inlining request
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    return if (isArgument) {
      frame.arguments[slot]
    } else {
      frame.getObject(slot)
    }
  }
}