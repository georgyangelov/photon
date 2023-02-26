package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*

class UnknownNode(override val type: Type): PhotonNode() {
  override fun isOperation(): Boolean = true
  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean = false
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any = this
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode = this
}