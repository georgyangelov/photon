package photon.compiler

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.core.PhotonFunction

class PhotonModule(
  language: TruffleLanguage<*>,

  val functions: List<PhotonFunction>,
  val mainFunction: PhotonFunction
): PhotonRootNode(language, mainFunction.body.frameDescriptor) {
  class Builder {
    private val functions = mutableListOf<PhotonFunction>()
    private lateinit var mainFunction: PhotonFunction

    fun addFunction(function: PhotonFunction): Builder {
      functions.add(function)

      return this
    }

    fun main(function: PhotonFunction): Builder {
      functions.add(function)
      mainFunction = function

      return this
    }

    fun build(language: PhotonLanguage) = PhotonModule(language, functions, mainFunction)
  }

  @CompilationFinal
  private var partiallyEvaluated = false

  override fun executePartial() {
    functions.forEach { it.body.executePartial() }
  }

  override fun execute(frame: VirtualFrame): Any {
    // TODO: Should this be thread-safe?
    if (!partiallyEvaluated) {
      executePartial()
      partiallyEvaluated = true
    }

    return mainFunction.body.execute(frame)
  }
}