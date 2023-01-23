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
import photon.frontend.ArgumentsWithoutSelf

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
  var valueLib: ValueLibrary = LibraryFactory.resolve(ValueLibrary::class.java).createDispatched(3)

  @JvmField
  @Child
  var typeLib: TypeLibrary = LibraryFactory.resolve(TypeLibrary::class.java).createDispatched(3)

  @JvmField
  @Child
  var methodLib: MethodLibrary = LibraryFactory.resolve(MethodLibrary::class.java).createDispatched(3)

  @CompilationFinal
  private var method: Method? = null

  var alreadyPartiallyExecuted = false

  @ExplodeLoop
  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    if (alreadyPartiallyExecuted) {
      return this
    }
    alreadyPartiallyExecuted = true

    CompilerAsserts.compilationConstant<Int>(arguments.size)

    target = target.executePartial(frame, context)

    for (i in arguments.indices) {
      arguments[i] = arguments[i].executePartial(frame, context)
    }

    // TODO: This should use the ValueLibrary to get the type
    val resolvedMethod = typeLib.getMethod(target.type, name, arguments.map { it.type })
    // TODO: Location
      ?: throw EvalError("Could not find method $name on ${target.type}", null)

    method = resolvedMethod

    type = typeCheck(resolvedMethod)

    if (resolvedMethod.methodType() == MethodType.Partial) {
      val result = executeCompileTimeOnly(frame)

      type = valueLib.type(result)

      // TODO: Literal or should this become "Any"?
      return LiteralNode(result, type, null)
    }

    return this
  }

  private fun typeCheck(method: Method): Type {
    val givenArgumentTypes = ArgumentsWithoutSelf(
      arguments.map { it.type },
      emptyMap()
    )
    val (signature, conversions) = method.signature().instantiate(givenArgumentTypes).getOrThrowError()

    for (i in arguments.indices) {
      val targetType = signature.argTypes[i].second
      val conversionNode = TypeConvertNode(targetType, conversions[i], arguments[i])

      arguments[i] = conversionNode
    }

    return signature.returnType
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