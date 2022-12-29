package photon.compiler.operations

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*

class PUnknown(override val type: Type) : Value() {
  override fun isOperation(): Boolean = true
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any = this
  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value = this
}