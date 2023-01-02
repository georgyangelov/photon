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
  private val executionNode = RootFunctionExecutionNode(language, frameDescriptor, this)

  fun executePartial(module: PhotonModule) {
    CompilerDirectives.transferToInterpreter()

    val partialFrameDescriptor = FrameTools.copyOfFrameDescriptorForPartialExecution(frameDescriptor)

    val executionNode = RootFunctionPartialExecutionNode(module, language, partialFrameDescriptor, this)

    executionNode.callTarget.call(emptyArray<Any>()) as PhotonNode
  }

  fun call(captures: Array<Any>, vararg arguments: Any): Any {
    return executionNode.callTarget.call(captures, *arguments)
  }
}

private class RootFunctionPartialExecutionNode(
  private val module: PhotonModule,
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  val fn: PhotonModuleRootFunction
): RootNode(language, frameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    val evalMode = EvalMode.Partial
    val context = PartialContext(module, evalMode)

    val languageContext = PhotonContext.currentFor(this)
    FrameTools.applyGlobalsToFramePartial(frame, languageContext)

    return fn.body.executePartial(frame, context)
  }
}

private class RootFunctionExecutionNode(
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  val fn: PhotonModuleRootFunction
): RootNode(language, frameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    val languageContext = PhotonContext.currentFor(this)
    FrameTools.applyGlobalsToFrame(frame, languageContext)

    return fn.body.executeCompileTimeOnly(frame)
  }
}