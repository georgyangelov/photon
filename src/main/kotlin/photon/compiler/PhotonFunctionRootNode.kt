package photon.compiler

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.*
import photon.core.EvalError

class PhotonFunctionRootNode(
  language: TruffleLanguage<*>,

  private val isMainModuleFunction: Boolean,
  val unevaluatedArgumentTypes: List<Value>,

  @CompilerDirectives.CompilationFinal
  private var body: Value,

  frameDescriptor: FrameDescriptor,
  val captures: Array<NameCapture>,
  val argumentCaptures: Array<ArgumentCapture>,

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
    val context = PartialContext(module, evalMode)

    val capturedValues =
      if (captures.isNotEmpty() && parentPartialFrame != null)
        FrameTools.captureValuesPartial(parentPartialFrame, captures)
      else
        emptyArray()

    val partialFrameDescriptor = FrameTools.copyOfFrameDescriptorForPartialExecution(frameDescriptor)

    // TODO: This can have arguments
    val partialRootNode = PhotonFunctionRootNodePartial(
      language,
      body,
      partialFrameDescriptor,
      context,
      captures,
      evaluatedArgumentTypes.toTypedArray(),
      argumentCaptures,
      isMainModuleFunction
    )

    body = partialRootNode.callTarget.call(capturedValues) as Value
  }

  @ExplodeLoop
  override fun execute(frame: VirtualFrame): Any {
    if (isMainModuleFunction) {
      val context = PhotonContext.currentFor(this)
      FrameTools.applyGlobalsToFrame(frame, context)
    }

    FrameTools.applyCapturedValuesFromFirstArgument(frame, captures)
    FrameTools.applyArguments(frame, argumentCaptures)

    return body.executeCompileTimeOnly(frame)
  }
}

private class PhotonFunctionRootNodePartial(
  language: TruffleLanguage<*>,
  val value: Value,
  partialFrameDescriptor: FrameDescriptor,
  val context: PartialContext,
  val captures: Array<NameCapture>,
  val argumentTypes: Array<Type>,
  val argumentCaptures: Array<ArgumentCapture>,
  val isMainModuleFunction: Boolean
): RootNode(language, partialFrameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    if (isMainModuleFunction) {
      val context = PhotonContext.currentFor(this)

      FrameTools.applyGlobalsToFramePartial(frame, context)
    }

    FrameTools.applyCapturedValuesFromFirstArgumentPartial(frame, captures)
    FrameTools.applyArgumentsForPartialExecution(frame, argumentCaptures, argumentTypes)

    return value.executePartial(frame, context)
  }
}