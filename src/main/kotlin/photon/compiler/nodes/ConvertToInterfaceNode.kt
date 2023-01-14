package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.compiler.values.PhotonInterfaceInstance

// TODO: Can this be unified with TypeAssertNode?
class ConvertToInterfaceNode(
  override val type: Type,
  val methodTable: Map<String, Method>,
  @Child @JvmField var valueNode: PhotonNode
): PhotonNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val value = valueNode.executeCompileTimeOnly(frame)

    return PhotonInterfaceInstance(type, value, methodTable)
  }
}