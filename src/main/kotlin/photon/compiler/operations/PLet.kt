package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.*
import photon.core.Location

class PLet(
  @JvmField val name: String,
  val slot: Int,
  @Child @JvmField var value: Value,
  @Child @JvmField var body: Value,
  val location: Location?
): Value() {
  override fun isOperation(): Boolean = true

  override fun typeOf(frame: VirtualFrame): Type = body.typeOf(frame)

  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any {
    // TODO: Support not fully evaluating everything
    val eValue = value.executeGeneric(frame, evalMode)
    frame.setObject(slot, eValue)

    val eBody = body.executeGeneric(frame, evalMode)

    return eBody
  }
}