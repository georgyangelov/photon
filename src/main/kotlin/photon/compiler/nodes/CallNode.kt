package photon.compiler.nodes

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.library.LibraryFactory
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.compiler.libraries.*
import photon.core.EvalError

class CallNode(
  @JvmField @Child var target: PhotonNode,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<PhotonNode>
): OperationNode() {
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
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    CompilerAsserts.compilationConstant<Int>(arguments.size)

    target = target.executePartial(frame, context)

    for (i in arguments.indices) {
      arguments[i] = arguments[i].executePartial(frame, context)
    }

    val resolvedMethod = typeLib.getMethod(target.type, name)
      // TODO: Location
      ?: throw EvalError("Could not find method $name on ${target.type}", null)

    method = resolvedMethod
    type = resolvedMethod.signature().returnType

    // TODO: Call the method if it's compile-time-only or partial

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