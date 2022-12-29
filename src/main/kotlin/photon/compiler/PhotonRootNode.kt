package photon.compiler

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.*

abstract class PhotonRootNode(
  language: TruffleLanguage<*>,
  frameDescriptor: FrameDescriptor
): RootNode(language, frameDescriptor) {
  abstract fun executePartial(frame: VirtualFrame)
}