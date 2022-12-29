package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.LibraryFactory
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.*
import photon.compiler.libraries.*
import photon.core.EvalError

class PCall(
  @JvmField @Child var target: Value,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<Value>
): Value() {
  @JvmField
  @Child
  var interop = InteropLibrary.getFactory().createDispatched(3)

  @JvmField
  @Child
  var typeLib = LibraryFactory.resolve(TypeLibrary::class.java).createDispatched(3)

  @JvmField
  @Child
  var methodLib = LibraryFactory.resolve(MethodLibrary::class.java).createDispatched(3)

  @CompilationFinal
  private var method: Method? = null

  private fun resolveMethod(frame: VirtualFrame) {
    val type = target.typeOf(frame)

    method = typeLib.getMethod(type, name)
    if (method == null) {
      // TODO: Location
      throw EvalError("Could not find method $name on $type", null)
    }
  }

  override fun isOperation(): Boolean = true

  override fun typeOf(frame: VirtualFrame): Type {
    if (method == null) {
      resolveMethod(frame)
    }

    return method!!.signature().returnType
  }

  @Suppress("UNCHECKED_CAST")
  @ExplodeLoop
  // TODO: Cache evalMode or different specializations for eval mode?
  // TODO: This should cache the target type & the function to be called
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

//    if (CompilerDirectives.inInterpreter()) {
    if (method == null) {
      resolveMethod(frame)
    }
//    }

    val evaluatedTarget = target.executeGeneric(frame, evalMode)

    val evaluatedArguments = arrayOfNulls<Any>(arguments.size)
    for (i in arguments.indices) {
      evaluatedArguments[i] = arguments[i].executeGeneric(frame, evalMode)
    }

    return methodLib.call(method!!, evalMode, evaluatedTarget, *(evaluatedArguments as Array<Any>))
  }
}