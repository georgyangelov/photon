package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class LetNode(
  @JvmField val name: String,
  val slot: Int,
  @Child @JvmField var value: PhotonNode,
  @Child @JvmField var body: PhotonNode,
  val location: Location?
): OperationNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    // TODO: Will this handle recursive references? Or maybe help detecting them?
    // metadata.localTypes[slot] = valueResult.type

    val valueResult = value.executePartial(frame, context)

    frame.setAuxiliarySlot(slot, valueResult)

    if (!valueResult.isOperation()) {
      frame.setObject(slot, valueResult)
    } else {
      frame.setAuxiliarySlot(slot, valueResult)
    }

    val bodyResult = body.executePartial(frame, context)

    value = valueResult
    body = bodyResult
    type = bodyResult.type

    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val eValue = value.executeCompileTimeOnly(frame)
    frame.setObject(slot, eValue)

    val eBody = body.executeCompileTimeOnly(frame)

    return eBody
  }
}