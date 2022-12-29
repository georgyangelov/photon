package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.*
import photon.compiler.core.*
import photon.compiler.types.FunctionType
import photon.core.EvalError

class PFunctionDefinition(
  val argumentTypes: List<Value>,
  val body: Value,
  val frameDescriptor: FrameDescriptor,
  val captures: List<NameCapture>
): Operation() {
  @CompilationFinal
  private var function: PhotonFunction? = null

  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    CompilerAsserts.neverPartOfCompilation()

    if (function != null) {
      // TODO: Location
      throw EvalError("Function definition is already evaluated in a partial context", null)
    }

    val rootNode = PhotonFunctionRootNode(
      language = context.module.getLanguage(PhotonLanguage::class.java),
      unevaluatedArgumentTypes = argumentTypes,
      body = body,
      frameDescriptor = frameDescriptor,
      captures = captures,
      parentPartialFrame = frame.materialize(),
      isMainModuleFunction = false
    )
    val function = PhotonFunction(rootNode)
    this.function = function

    context.module.addFunction(function)

    // TODO: Different function type for each function
    type = FunctionType()

    return this
  }

  @Suppress("UNCHECKED_CAST")
  @ExplodeLoop
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    assert(function != null)

    CompilerAsserts.compilationConstant<Int>(captures.size)

    val capturedValues = arrayOfNulls<Any>(captures.size)
    for (i in capturedValues.indices) {
      val captureFrom = captures[i].from

      capturedValues[i] = if (captureFrom.type == Name.Type.Argument) {
        frame.arguments[captureFrom.slotOrArgumentIndex]
      } else {
        frame.getObject(captureFrom.slotOrArgumentIndex)
      }
    }

    return PClosure(function!!, capturedValues as Array<Any>, type)
  }
}