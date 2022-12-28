package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.*

class PCall(
  @JvmField @Child var target: Value,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<Value>
): Value() {
  @JvmField
  @Child
  var interop = InteropLibrary.getFactory().createDispatched(3)

  override fun typeOf(frame: VirtualFrame): Type {
    return arguments[arguments.size - 1].typeOf(frame)
  }

  @ExplodeLoop
  // TODO: Cache evalMode or different specializations for eval mode?
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

    val evaluatedTarget = target.executeGeneric(frame, evalMode)

    val evaluatedArguments = arrayOfNulls<Any>(arguments.size + 1)
    evaluatedArguments[0] = evaluatedTarget

    for (i in arguments.indices) {
      evaluatedArguments[i + 1] = arguments[i].executeGeneric(frame, evalMode)
    }

    return interop.invokeMember(evaluatedTarget, name, *evaluatedArguments)
  }
}