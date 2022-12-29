package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.*
import photon.compiler.libraries.PhotonLibrary

class PCall(
  @JvmField @Child var target: Value,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<Value>
): Value() {
  @JvmField
  @Child
//  val interop = PhotonLibraryGen
  var interop = InteropLibrary.getFactory().createDispatched(3)

  override fun isOperation(): Boolean = true

  override fun typeOf(frame: VirtualFrame): Type {
    return arguments[arguments.size - 1].typeOf(frame)
  }

  @ExplodeLoop
  // TODO: Cache evalMode or different specializations for eval mode?
  // TODO: This should cache the target type & the function to be called
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

    val evaluatedTarget = target.executeGeneric(frame, evalMode)

    val evaluatedArguments = arrayOfNulls<Any>(arguments.size)
    for (i in arguments.indices) {
      evaluatedArguments[i] = arguments[i].executeGeneric(frame, evalMode)
    }

    // TODO: Instead of invoke, this should find the method given the type of the expression
    //       Because we want to be able to execute methods even if their target values are not
    //       yet known.
    return interop.invokeMember(evaluatedTarget, name, *evaluatedArguments)
  }
}