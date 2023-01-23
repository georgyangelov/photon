package photon.compiler.core

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.*
import photon.compiler.nodes.LiteralNode
import photon.compiler.nodes.PatternNode

class PhotonTemplateFunction(
  private val module: PhotonModule,
  private val typeFrameDescriptor: FrameDescriptor,
  private val executionFrameDescriptor: FrameDescriptor,

  // TODO: isCompileTimeOnly?

  private val partialEvalFrame: MaterializedFrame,
  val argumentPatterns: List<Pair<String, PatternNode>>,
  val returnType: PhotonNode?,

  private val requiredCaptures: Array<NameCapture>,
  private val argumentCaptures: Array<ArgumentCapture>,
  private val body: PhotonNode
) {
  val type: Type
    get() = TODO()

  private val executionNode = PatternExecutionNode(
    module = module,
    frameDescriptor = FrameTools.copyOfFrameDescriptorForPartialExecution(typeFrameDescriptor),
    fn = this
  )

  fun specialize(argumentTypes: List<Type>): PhotonFunction {
    val (expectedArgTypes, returnType) = executionNode.callTarget.call(argumentTypes.toTypedArray())
      as Pair<Array<Type>, Type?>

    return PhotonFunction(
      module = module,
      frameDescriptor = executionFrameDescriptor,
      isCompileTimeOnly = false,
      partialEvalFrame = partialEvalFrame,

      // TODO: Should I pass the types directly?
      argumentTypes = argumentPatterns
        .zip(expectedArgTypes)
        .map { Pair(it.first.first, LiteralNode(it.second, RootType, null)) },

      returnType = if (returnType != null) LiteralNode(returnType, RootType, null) else null,

      requiredCaptures = requiredCaptures,
      argumentCaptures = argumentCaptures,
      body = body
    )
  }

  private class PatternExecutionNode(
    private val module: PhotonModule,
    frameDescriptor: FrameDescriptor,

    val fn: PhotonTemplateFunction
  ): RootNode(module.getLanguage(PhotonLanguage::class.java), frameDescriptor) {
    override fun execute(frame: VirtualFrame): Any {
      val argTypes = arrayOfNulls<Type>(fn.argumentPatterns.size)

      // TODO: Add auxiliary slots to the frame
      val partialContext = PartialContext(module, EvalMode.Partial)

      for (i in fn.argumentPatterns.indices) {
        // TODO: Define slot 0 in the frame descriptor
        frame.setAuxiliarySlot(0, LiteralNode(frame.arguments[i], RootType, null))
        frame.setObject(0, frame.arguments[i])

        val partialResult = fn.argumentPatterns[i].second.executePartial(frame, partialContext)

        // TODO: Verify it's actually a Type
        argTypes[i] = partialResult.executeCompileTimeOnly(frame) as Type
      }

      val returnType =
        if (fn.returnType != null) {
          val partialResult = fn.returnType.executePartial(frame, partialContext)

          partialResult.executeCompileTimeOnly(frame)
        } else null

      return Pair(argTypes, returnType)
    }
  }
}