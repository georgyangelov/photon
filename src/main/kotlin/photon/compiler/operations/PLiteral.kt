package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.*
import photon.core.Location

class PLiteral(
  @JvmField val value: Any,
  private val type: Type,
  val location: Location?
): Value() {
  override fun isOperation(): Boolean = true
  override fun typeOf(frame: VirtualFrame): Type = type
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any = value
}