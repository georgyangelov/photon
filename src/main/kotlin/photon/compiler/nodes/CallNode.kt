package photon.compiler.nodes

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.LibraryFactory
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.compiler.libraries.*
import photon.compiler.types.AnyType
import photon.core.EvalError
import photon.frontend.ArgumentsWithoutSelf

class CallNode(
  @JvmField @Child var target: PhotonNode,
  @JvmField val name: String,
  @JvmField @Children var arguments: Array<PhotonNode>
): OperationNode() {
  @JvmField
  @Child
  var interop: InteropLibrary = InteropLibrary.getFactory().createDispatched(3)

  @JvmField
  @Child
  var valueLib: PhotonValueLibrary = LibraryFactory.resolve(PhotonValueLibrary::class.java).createDispatched(3)

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

    if (target.type == AnyType) {
      type = AnyType
      return this
    }

    val resolvedMethod = target.type.getMethod(name, arguments.map { it.type })
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

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean {
    // Reaching this means that the `executePartial` function above did not return a concrete value,
    // meaning there is still work to be done when calling this compile-time
    return false
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

    if (type == AnyType) {
      // TODO: Check and throw error if not invokable
      return interop.invokeMember(evaluatedTarget, name, *evaluatedArguments)
    }

    return method!!.call(evaluatedTarget, *(evaluatedArguments as Array<Any>))
  }
}