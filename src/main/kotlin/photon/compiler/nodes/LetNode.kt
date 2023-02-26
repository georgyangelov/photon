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
//  companion object {
//    val RECURSIVE_DEFINITION_TOKEN = "recursive"
//  }

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    // TODO: Not sure if this is 100% correct, but using it so that the
    //       `canBeCapturedDuringPartialEvaluation` function can ignore recursive references of
    //       partial-only functions
    // frame.setAuxiliarySlot(slot, RECURSIVE_DEFINITION_TOKEN)

    val valueResult = value.executePartial(frame, context)

    frame.setAuxiliarySlot(slot, valueResult)

    if (valueResult.canBeCapturedDuringPartialEvaluation(frame)) {
      frame.setObject(slot, valueResult.executeCompileTimeOnly(frame))
    }

    value = valueResult
    type = valueResult.type

    return this
  }

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean =
    value.canBeCapturedDuringPartialEvaluation(frame)

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val eValue = value.executeCompileTimeOnly(frame)
    frame.setObject(slot, eValue)

    return eValue
  }
}