package photon.compiler.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.library.LibraryFactory
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.core.TypeError

class TypeAssertNode(
  @Child @JvmField var valueNode: PhotonNode,
  @Child @JvmField var typeNode: PhotonNode
): OperationNode() {
  @JvmField
  @Child
  var valueLib: ValueLibrary = LibraryFactory.resolve(ValueLibrary::class.java).createDispatched(3)

  @CompilationFinal
  private var alreadyPartiallyEvaluated = false

  private var evaluatedValue: Any? = null

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    if (alreadyPartiallyEvaluated) {
      return this
    }

    alreadyPartiallyEvaluated = true

    typeNode = typeNode.executePartial(frame, context)

    // TODO: Verify it's actually a type
    val evaluatedType = typeNode.executeCompileTimeOnly(frame) as Type

    valueNode = valueNode.executePartial(frame, context)

    if (evaluatedType == AnyStatic) {
      val evaluatedValue = valueNode.executeCompileTimeOnly(frame)

      this.evaluatedValue = evaluatedValue
      type = valueLib.type(evaluatedValue)
    } else {
      valueNode = when (val result = Core.isTypeAssignable(valueNode.type, evaluatedType)) {
        is PossibleTypeError.Error -> throw TypeError(
          "Cannot assign ${valueNode.type} to $evaluatedType: ${result.error.message}",
          result.error.location
        )

        is PossibleTypeError.Success -> result.value(valueNode)
      }

      type = evaluatedType
    }

    // TODO: Return a different node which wraps the target value if the target type is an interface
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    if (evaluatedValue != null) {
      return evaluatedValue!!
    }

    val result = valueNode.executeCompileTimeOnly(frame)

    evaluatedValue = result

    return result
  }
}