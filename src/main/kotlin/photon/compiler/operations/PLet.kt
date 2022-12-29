package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

class PLet(
  @JvmField val name: String,
  val slot: Int,
  @Child @JvmField var value: Value,
  @Child @JvmField var body: Value,
  val location: Location?
): Operation() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    // TODO: Will this handle recursive references? Or maybe help detecting them?
    // metadata.localTypes[slot] = valueResult.type

    val valueResult = value.executePartial(frame, context)

    context.localTypes[slot] = valueResult.type

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