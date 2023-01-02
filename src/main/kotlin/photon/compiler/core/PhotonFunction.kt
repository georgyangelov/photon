package photon.compiler.core

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.*
import photon.compiler.types.FunctionType

class PhotonFunction(
  private val module: PhotonModule,
  private val frameDescriptor: FrameDescriptor,

  internal val partialEvalFrame: MaterializedFrame,
  private val argumentTypes: List<Pair<String, PhotonNode>>,
  private val returnType: PhotonNode?,

  internal val requiredCaptures: Array<NameCapture>,
  internal val argumentCaptures: Array<ArgumentCapture>,
  internal val body: PhotonNode
) {
  private var alreadyPartiallyEvaluated = false
  private val executionNode = ExecutionNode(module.getLanguage(PhotonLanguage::class.java), frameDescriptor, this)

  val type = FunctionType(this)

  @CompilationFinal
  internal var actualArgumentTypes: List<Pair<String, Type>>? = null

  @CompilationFinal
  internal var actualReturnType: Type? = null

  internal fun resolveArgumentTypes() {
    if (actualArgumentTypes != null) {
      return
    }

    CompilerDirectives.transferToInterpreterAndInvalidate()

    actualArgumentTypes = argumentTypes.map {
      val (name, unevaluatedType) = it

      // TODO: Some error messaging if the value is not a type
      val type = unevaluatedType.executeCompileTimeOnly(partialEvalFrame) as Type

      Pair(name, type)
    }
  }

  internal fun resolveReturnType() {
    if (actualReturnType != null) {
      return
    }

    if (returnType != null) {
      // TODO: Some error messaging if the value is not a type
      actualReturnType = returnType.executeCompileTimeOnly(partialEvalFrame) as Type
    } else {
      executePartial(module)

      actualReturnType = body.type
    }
  }

  fun executePartial(module: PhotonModule) {
    if (alreadyPartiallyEvaluated) {
      return
    }

    CompilerDirectives.transferToInterpreter()
    alreadyPartiallyEvaluated = true

    val partialFrameDescriptor = FrameTools.copyOfFrameDescriptorForPartialExecution(frameDescriptor)

    val capturedValues = FrameTools.captureValuesPartial(partialEvalFrame, requiredCaptures)
    val language = module.getLanguage(PhotonLanguage::class.java)
    val executionNode = PartialExecutionNode(module, language, partialFrameDescriptor, this)

    executionNode.callTarget.call(capturedValues) as PhotonNode
  }

  fun call(captures: Array<Any>, vararg arguments: Any): Any {
    return executionNode.callTarget.call(captures, *arguments)
  }
}

private class PartialExecutionNode(
  private val module: PhotonModule,
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  val fn: PhotonFunction
): RootNode(language, frameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    // TODO: This should know the correct eval mode. Probably.
    val evalMode = EvalMode.Partial
    val context = PartialContext(module, evalMode)

    fn.resolveArgumentTypes()
    val argumentTypes = fn.actualArgumentTypes!!.map { it.second }.toTypedArray()

    FrameTools.applyCapturedValuesFromFirstArgumentPartial(frame, fn.requiredCaptures)
    FrameTools.applyArgumentsForPartialExecution(frame, fn.argumentCaptures, argumentTypes)

    return fn.body.executePartial(frame, context)
  }
}

private class ExecutionNode(
  language: PhotonLanguage,
  frameDescriptor: FrameDescriptor,

  val fn: PhotonFunction
): RootNode(language, frameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    FrameTools.applyCapturedValuesFromFirstArgument(frame, fn.requiredCaptures)
    FrameTools.applyArguments(frame, fn.argumentCaptures)

    return fn.body.executeCompileTimeOnly(frame)
  }
}