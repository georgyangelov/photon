package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.*
import photon.compiler.core.*
import photon.compiler.types.FunctionType

class PFunctionDefinition(
  val argumentTypes: List<Value>,
  val body: Value,
  val frameDescriptor: FrameDescriptor
): Operation() {
  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    CompilerAsserts.neverPartOfCompilation()

    val rootNode = PhotonFunctionRootNode(
      language = context.module.getLanguage(PhotonLanguage::class.java),
      unevaluatedArgumentTypes = argumentTypes,
      body = body,
      frameDescriptor = frameDescriptor,
      parentPartialFrame = frame.materialize()
    )
    val function = PhotonFunction(rootNode)

    context.module.addFunction(function)

    // TODO: Specify location
    return PLiteral(function, FunctionType(), null)
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    CompilerAsserts.neverPartOfCompilation()
    CompilerDirectives.shouldNotReachHere("Should not be able to execute PFunctionDefinition except partially")

    throw RuntimeException("Should not be able to execute PFunctionDefinition except partially")
  }
}