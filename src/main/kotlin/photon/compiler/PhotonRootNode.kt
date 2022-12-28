package photon.compiler

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.EvalMode
import photon.compiler.core.Value

class PhotonRootNode(
  language: TruffleLanguage<*>,
  private val value: Value,
  frameDescriptor: FrameDescriptor
): RootNode(language, frameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    // TODO: Switch eval modes when needed
    return value.executeGeneric(frame, EvalMode.CompileTimeOnly)
  }
}