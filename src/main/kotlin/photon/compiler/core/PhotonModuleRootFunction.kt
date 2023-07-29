package photon.compiler.core

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.*

class PhotonModuleRootFunction(
  private val language: PhotonLanguage,
  val frameDescriptor: FrameDescriptor,

  internal val body: PhotonNode
) {
  fun executePartial(module: PhotonModule) {
    CompilerDirectives.transferToInterpreter()

    val partialFrameDescriptor = FrameTools.copyOfFrameDescriptorForPartialExecution(frameDescriptor)

    val executionNode = RootFunctionPartialExecutionNode(module, language, partialFrameDescriptor, this)

    executionNode.callTarget.call(emptyArray<Any>()) as PhotonNode
  }

  fun call(captures: Array<Any>, vararg arguments: Any): Any {
    val executionNode = RootFunctionExecutionNode(language, frameDescriptor, this)

    return executionNode.callTarget.call(captures, *arguments)
  }
}

private class RootFunctionPartialExecutionNode(
  private val module: PhotonModule,
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  fn: PhotonModuleRootFunction
): RootNode(language, frameDescriptor) {
  @Child var fnBody = fn.body

  override fun execute(frame: VirtualFrame): Any {
    val evalMode = EvalMode.Partial
    val context = PartialContext(module, evalMode)

    val languageContext = PhotonContext.currentFor(this)
    FrameTools.applyGlobalsToFramePartial(frame, languageContext)

    return fnBody.executePartial(frame, context)
  }
}

private class RootFunctionExecutionNode(
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  fn: PhotonModuleRootFunction
): RootNode(language, frameDescriptor) {
  @Child var fnBody = fn.body

  override fun execute(frame: VirtualFrame): Any {
    val languageContext = PhotonContext.currentFor(this)
    FrameTools.applyGlobalsToFrame(frame, languageContext)

    return fnBody.executeCompileTimeOnly(frame)
  }
}