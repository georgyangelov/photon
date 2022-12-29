package photon.compiler.operations

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.library.LibraryFactory
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.*
import photon.compiler.libraries.*
import photon.core.EvalError

class PCall(
  @JvmField @Child var target: Value,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<Value>
): Operation() {
//  @JvmField
//  @Child
//  var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

  @JvmField
  @Child
  var typeLib: TypeLibrary = LibraryFactory.resolve(TypeLibrary::class.java).createDispatched(3)

  @JvmField
  @Child
  var methodLib: MethodLibrary = LibraryFactory.resolve(MethodLibrary::class.java).createDispatched(3)

  @CompilationFinal
  private var method: Method? = null

  @ExplodeLoop
  override fun executePartial(frame: PartialFrame, evalMode: EvalMode): Value {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

    target = target.executePartial(frame, evalMode)

    for (i in arguments.indices) {
      arguments[i] = arguments[i].executePartial(frame, evalMode)
    }

    val resolvedMethod = typeLib.getMethod(target.type, name)
      // TODO: Location
      ?: throw EvalError("Could not find method $name on $type", null)

    method = resolvedMethod
    type = resolvedMethod.signature().returnType

    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

    val evaluatedTarget = target.executeCompileTimeOnly(frame)

    val evaluatedArguments = arrayOfNulls<Any>(arguments.size)
    for (i in arguments.indices) {
      evaluatedArguments[i] = arguments[i].executeCompileTimeOnly(frame)
    }

    return methodLib.call(
      method!!,
      EvalMode.CompileTimeOnly,
      evaluatedTarget,
      *(evaluatedArguments as Array<Any>)
    )
  }
}