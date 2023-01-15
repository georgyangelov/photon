package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*

class TypeConvertNode(
  override val type: Type,
  val convertor: ValueConvertor,
  @Child @JvmField var valueNode: PhotonNode
): PhotonNode() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    val value = valueNode.executeCompileTimeOnly(frame)
    val converted = convertor(value)

    return converted
  }
}