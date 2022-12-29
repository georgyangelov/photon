package photon.compiler

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.EvalMode
import photon.compiler.core.PhotonFunction
import photon.frontend.ASTValue

class PhotonModule(
  language: TruffleLanguage<*>,
  val main: PhotonFunction
): RootNode(language, main.body.frameDescriptor) {
  @CompilationFinal
  private var partiallyEvaluated = false

  @CompilationFinal
  private val functions = mutableListOf<PhotonFunction>()

  fun addFunction(function: PhotonFunction) {
    if (partiallyEvaluated) {
      CompilerDirectives.shouldNotReachHere("Functions can only be defined during the partial execution step")
    }

    functions.add(function)
  }

  private fun executePartial() {
    main.body.executePartial(this)

    // We're iterating like this on purpose because any executePartial call may define additional functions
    // which we'll need to also execute partially.
    // We're also using the assumption that we'll only be appending to the end of the list
    var i = 0
    while (i < functions.size) {
      functions[i].body.executePartial(this)

      i++
    }
  }

  override fun execute(frame: VirtualFrame): Any {
    // TODO: Should this be thread-safe?
    if (!partiallyEvaluated) {
      CompilerDirectives.transferToInterpreterAndInvalidate()

      executePartial()
      partiallyEvaluated = true
    }

    return main.body.callTarget.call(frame.arguments)
  }
}