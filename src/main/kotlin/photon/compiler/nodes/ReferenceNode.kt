package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class ReferenceNode(
  val name: String,
  private val slot: Int,
  val location: Location?
): OperationNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    val partialValue = frame.getAuxiliarySlot(slot) as PhotonNode?
      ?: throw RuntimeException("Cannot get type of reference, metadata does not contain slot $slot. $frame")

    type = partialValue.type

    // TODO: Inlining request?
    return this
  }

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean =
    frame.getObject(slot) != null

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val value = frame.getObject(slot)

    // TODO: This should only be called when we have a frame for partial evaluation
    return if (value == null) {
      val partialValue = frame.getAuxiliarySlot(slot) as PhotonNode
      partialValue.executeCompileTimeOnly(frame)
    } else value
  }
}