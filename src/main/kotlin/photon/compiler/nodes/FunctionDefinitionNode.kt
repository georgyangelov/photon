package photon.compiler.nodes

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.Node
import photon.compiler.*
import photon.compiler.core.*
import photon.compiler.values.Closure
import photon.core.EvalError

class FunctionDefinitionNode(
  @Children @JvmField var argumentTypes: Array<ParameterNode>,
  @Child @JvmField var returnType: PhotonNode?,
  @Child @JvmField var body: PhotonNode,

  val isCompileTimeOnly: Boolean,

  val frameDescriptor: FrameDescriptor,
  val captures: Array<NameCapture>,
  val argumentCaptures: Array<ArgumentCapture>
): OperationNode() {
  class ParameterNode(
    @JvmField val name: String,
    @Child @JvmField var type: PhotonNode
  ): Node()

  @CompilationFinal
  private var function: PhotonFunction? = null

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean {
    // This returning `true` means that it's ok for this closure to be captured at partial time
    return captures.all {
      val hasActualValue = frame.getObject(it.fromSlot) != null
      // TODO: Currently partial-only functions cannot call recursive functions. Support that
//      val isRecursiveReference = frame.getAuxiliarySlot(it.fromSlot) == LetNode.RECURSIVE_DEFINITION_TOKEN

      hasActualValue // || isRecursiveReference
    }
  }

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    CompilerAsserts.neverPartOfCompilation()

    if (function != null) {
      // TODO: Location
      throw EvalError("Function definition is already evaluated in a partial context", null)
    }

    val function = PhotonFunction(
      module = context.module,
      frameDescriptor = frameDescriptor,

      isCompileTimeOnly = isCompileTimeOnly,

      partialEvalFrame = frame.materialize(),
      argumentTypes = argumentTypes.map { Pair(it.name, it.type) },
      returnType = returnType,

      requiredCaptures = captures,
      argumentCaptures = argumentCaptures,
      body = body
    )

    this.function = function

    type = function.type

    context.module.addFunction(function)

    return this
  }

  @Suppress("UNCHECKED_CAST")
  @ExplodeLoop
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    assert(function != null)

    return Closure(function!!.type, frame.materialize())
  }
}