package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.OperationNode
import photon.compiler.core.PhotonNode

class TypeAssertNode(
  @Child @JvmField var valueNode: PhotonNode,
  @Child @JvmField var typeNode: PhotonNode
): OperationNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    TODO("Not yet implemented")
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    TODO("Not yet implemented")
  }
}