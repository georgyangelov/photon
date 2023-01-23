package photon.compiler.nodes

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.PartialContext
import photon.compiler.PhotonLanguage
import photon.compiler.core.*

sealed class PatternNode: OperationNode() {
  data class SpecificValue(val value: PhotonNode): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      type = value.type

      return value.executePartial(frame, context)
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      CompilerDirectives.shouldNotReachHere()
      throw RuntimeException("This shouldn't happen - executeCompileTimeOnly on PatternNode.SpecificValue")

      // return value.executeCompileTimeOnly(frame)
    }
  }

  data class Binding(val name: String, val slot: Int): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      val value = frame.getAuxiliarySlot(0)

      type = when (value) {
        is PhotonNode -> value.type
        else -> throw RuntimeException("Cannot get type of reference, metadata does not contain slot $slot. $frame")
      }

      return this
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      val value = frame.getObject(0)

      frame.setObject(slot, value)

      return value
    }
  }

  data class Call(
    val target: PhotonNode,
    val name: String,
    val arguments: List<PatternNode>
  ): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      TODO("Not yet implemented")
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      TODO("Not yet implemented")
    }
  }

  data class FunctionType(
    val params: List<Pair<String, PatternNode>>,
    val returnType: PatternNode
  ): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      TODO("Not yet implemented")
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      TODO("Not yet implemented")
    }
  }
}
