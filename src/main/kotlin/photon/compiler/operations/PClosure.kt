package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.PartialContext
import photon.compiler.core.*

class PClosure(
  val function: PhotonFunction,
  val captures: Array<Any>,
  override val type: Type
): Value() {
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any = this

  override fun executePartial(frame: VirtualFrame, context: PartialContext): Value {
    CompilerAsserts.neverPartOfCompilation()
    CompilerDirectives.shouldNotReachHere("Should not be able to execute PFunctionDefinition except partially")

    throw RuntimeException("Should not be able to execute PFunctionDefinition except partially")
  }
}