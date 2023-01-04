package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.BlockNode
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.core.EvalError
import photon.core.Location

class BlockNode(
  expressions: Array<PhotonNode>,
  val location: Location?
): OperationNode(), BlockNode.ElementExecutor<PhotonNode> {
  @Child
  private var truffleBlock: BlockNode<PhotonNode> =
    BlockNode.create(expressions, this)

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    var lastExpression: PhotonNode? = null

    for (expression in truffleBlock.elements) {
      lastExpression = expression.executePartial(frame, context)
    }

    if (lastExpression == null) {
      throw EvalError("Cannot have a block with no statements", location)
    }

    type = lastExpression.type

    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    return truffleBlock.executeGeneric(frame, 42)
  }

  override fun executeVoid(frame: VirtualFrame, node: PhotonNode, index: Int, argument: Int) {
    executeGeneric(frame, node, index, argument)
  }

  override fun executeGeneric(frame: VirtualFrame, node: PhotonNode, index: Int, argument: Int): Any {
    return node.executeCompileTimeOnly(frame)
  }
}
