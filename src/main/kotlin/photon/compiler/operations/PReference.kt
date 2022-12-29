package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class PReference(
  val name: String,
  private val slot: Int,
  val location: Location?
): Operation() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    val partialValue = frame.getAuxiliarySlot(slot) as Value?
      ?: throw RuntimeException("Cannot get type of reference, metadata does not contain slot $slot. $frame")

    type = partialValue.type

    // TODO: Inlining request?
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val value = frame.getObject(slot)

    // TODO: This should only be called when we have a frame for partial evaluation
    return if (value == null) {
      val partialValue = frame.getAuxiliarySlot(slot) as Value
      partialValue.executeCompileTimeOnly(frame)
    } else value
  }
}