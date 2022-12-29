package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.Location

// TODO: Do I want to export libraries and delegate to `value`?
class PLiteral(
  @JvmField val value: Any,
  override val type: Type,
  val location: Location?
): Value() {
  override fun isOperation(): Boolean = true

  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value = this
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any = value
}