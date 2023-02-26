package photon.compiler.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.BlockNode
import com.oracle.truffle.api.nodes.BlockNode.NO_ARGUMENT
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

    if (truffleBlock.elements.size == 1) {
      return lastExpression
    }

    return this
  }

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean {
    // Reaching this means that the `executePartial` function above did not return a single value,
    // meaning there is still work to be done when calling this compile-time
    return false
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    return truffleBlock.executeGeneric(frame, NO_ARGUMENT)
  }

  override fun executeVoid(frame: VirtualFrame, node: PhotonNode, index: Int, argument: Int) {
    executeGeneric(frame, node, index, argument)
  }

  override fun executeGeneric(frame: VirtualFrame, node: PhotonNode, index: Int, argument: Int): Any {
    return node.executeCompileTimeOnly(frame)
  }
}
