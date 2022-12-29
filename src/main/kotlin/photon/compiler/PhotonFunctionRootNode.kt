package photon.compiler

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.*

class PhotonFunctionRootNode(
  language: TruffleLanguage<*>,

  @CompilerDirectives.CompilationFinal
  private var value: Value,

  frameDescriptor: FrameDescriptor
): PhotonRootNode(language, frameDescriptor) {
  override fun executePartial() {
    CompilerDirectives.transferToInterpreter()

    val argumentTypes = emptyMap<Int, Type>()
    val frame = PartialFrame(argumentTypes)

    // TODO: This should know the correct eval mode. Probably.
    val evalMode = EvalMode.Partial

    value = value.executePartial(frame, evalMode)
  }

  override fun execute(frame: VirtualFrame): Any {
    return value.executeCompileTimeOnly(frame)
  }
}