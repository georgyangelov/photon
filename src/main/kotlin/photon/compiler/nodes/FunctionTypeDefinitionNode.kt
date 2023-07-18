package photon.compiler.nodes

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import photon.compiler.PartialContext
import photon.compiler.core.*
import photon.compiler.types.classes.PhotonFunctionalInterface
import photon.core.EvalError

class FunctionTypeDefinitionNode(
  @Children @JvmField var argumentTypes: Array<ParameterNode>,
  @Child @JvmField var returnType: PhotonNode,
): OperationNode() {
  class ParameterNode(
    @JvmField val name: String,
    @Child @JvmField var type: PhotonNode
  ): Node()

  @CompilerDirectives.CompilationFinal
  private var functionType: PhotonFunctionalInterface? = null

  override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
    if (functionType != null) {
      // TODO: Location
      throw EvalError("Function definition is already evaluated in a partial context", null)
    }

    CompilerAsserts.neverPartOfCompilation()

    // TODO: Check it's actually a Type
    val parameters = argumentTypes.map { Pair(it.name, it.type.executeCompileTimeOnly(frame) as Type) }

    // TODO: Check it's actually a Type
    val returns = returnType.executeCompileTimeOnly(frame) as Type

    val functionalInterface = PhotonFunctionalInterface(parameters, returns)

    functionType = functionalInterface
    type = functionalInterface

    return this
  }

  override fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean = true

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
    assert(functionType != null)

    return functionType!!
  }
}