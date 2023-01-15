package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class LetNode(
  @JvmField val name: String,
  val slot: Int,
  @Child @JvmField var value: PhotonNode,
  val location: Location?
): OperationNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    val valueResult = value.executePartial(frame, context)

    frame.setAuxiliarySlot(slot, valueResult)

    if (!valueResult.isOperation()) {
      frame.setObject(slot, valueResult)
    } else {
      frame.setAuxiliarySlot(slot, valueResult)
    }

    value = valueResult
    type = valueResult.type

    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val eValue = value.executeCompileTimeOnly(frame)
    frame.setObject(slot, eValue)

    return eValue
  }
}