package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class PReference(
  val name: String,
  private val isArgument: Boolean,
  private val slot: Int,
  val location: Location?
): Operation() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    val referencedType = if (isArgument) {
      context.argumentTypes[slot]
    } else {
      context.localTypes[slot]
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
      val value = frame.getObject(slot)

      // TODO: This should only be called when we have a frame for partial evaluation
      if (value == null) {
        val partialValue = frame.getAuxiliarySlot(slot) as Value
        partialValue.executeCompileTimeOnly(frame)
      } else value
    }
  }
}