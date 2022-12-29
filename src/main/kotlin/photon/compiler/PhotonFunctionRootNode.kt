package photon.compiler

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.*

class PhotonFunctionRootNode(
  language: TruffleLanguage<*>,

  val unevaluatedArgumentTypes: List<Value>,

  @CompilerDirectives.CompilationFinal
  private var body: Value,

  frameDescriptor: FrameDescriptor,

  // The main function of a module will not have a parent
  private val parentPartialFrame: MaterializedFrame?
): RootNode(language, frameDescriptor) {
  private var evaluatedArgumentTypes = mutableListOf<Type>()

  fun executePartial(module: PhotonModule) {
    CompilerDirectives.transferToInterpreter()

    if (parentPartialFrame != null) {
      for (typeValue in unevaluatedArgumentTypes) {
        // TODO: Guard against using values which are not yet executed
        val argumentType = typeValue.executeCompileTimeOnly(parentPartialFrame) as Type

        evaluatedArgumentTypes.add(argumentType)
      }
    } else if (unevaluatedArgumentTypes.isNotEmpty()) {
      CompilerDirectives.shouldNotReachHere("Main function of module cannot have argument types")
    }

    // TODO: This should know the correct eval mode. Probably.
    val evalMode = EvalMode.Partial

    val language = PhotonContext.currentFor(this).language
    val context = PartialContext(module, evalMode, evaluatedArgumentTypes)

    // TODO: This can have arguments
    val partialRootNode = PhotonFunctionRootNodePartial(language, body, frameDescriptor, context)

    body = partialRootNode.callTarget.call() as Value
  }

  override fun execute(frame: VirtualFrame): Any {
    // TODO: Move this to PhotonModule once we have real closures
    PhotonContext.currentFor(this).setGlobalsToFrame(frame)

    return body.executeCompileTimeOnly(frame)
  }
}

private class PhotonFunctionRootNodePartial(
  language: TruffleLanguage<*>,
  val value: Value,
  partialFrameDescriptor: FrameDescriptor,
  val context: PartialContext
): RootNode(language, partialFrameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    // TODO: Move this to PhotonModule once we have real closures
    PhotonContext.currentFor(this).setGlobalsToFrame(frame)

    return value.executePartial(frame, context)
  }
}