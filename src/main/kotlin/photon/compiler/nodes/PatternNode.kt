package photon.compiler.nodes

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.VirtualFrame
import photon.compiler.*
import photon.compiler.core.*
import photon.compiler.values.classes.PhotonFunctionalInterface
import photon.core.EvalError

sealed class PatternNode: OperationNode() {
  data class SpecificValue(@Child @JvmField var value: PhotonNode): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      value = value.executePartial(frame, context)
      type = value.type

      return value
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      CompilerDirectives.shouldNotReachHere()
      throw RuntimeException("This shouldn't happen - executeCompileTimeOnly on PatternNode.SpecificValue")

      // return value.executeCompileTimeOnly(frame)
    }
  }

  data class Binding(val name: String, val slot: Int): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      val value = frame.getAuxiliarySlot(0)

      type = when (value) {
        is PhotonNode -> value.type
        else -> throw RuntimeException("Cannot get type of reference, metadata does not contain slot 0. $frame")
      }

      frame.setAuxiliarySlot(slot, value)

      return this
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      val value = frame.getObject(0)

      frame.setObject(slot, value)

      return value
    }
  }

  data class Call(
    val target: PhotonNode,
    val name: String,
    val arguments: List<PatternNode>
  ): PatternNode() {
    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      TODO("Not yet implemented")
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      TODO("Not yet implemented")
    }
  }

  data class FunctionType(
    val params: List<Pair<String, PatternNode>>,
    val returnType: PatternNode
  ): PatternNode() {
    var module: PhotonModule? = null

    override fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode {
      val givenFunctionType = frame.getAuxiliarySlot(0)

      type = when (givenFunctionType) {
        is PhotonNode -> givenFunctionType.type
        else -> throw RuntimeException("Cannot get type of function type, metadata does not contain slot 0. $frame")
      }
      module = context.module

      return this
    }

    override fun executeCompileTimeOnly(frame: VirtualFrame): Any {
      val partialContext = PartialContext(module!!, EvalMode.Partial)

      val (givenArguments, givenReturnType) = when (val givenType = frame.getObject(0)) {
        is photon.compiler.types.FunctionType -> {
          givenType.function.resolveSignatureTypesWithInference()

          Pair(givenType.function.actualArgumentTypes!!, givenType.function.actualReturnType!!)
        }

        is PhotonFunctionalInterface -> Pair(givenType.parameters, givenType.returnType)

        is Interface -> {
          val callMethod = givenType.getMethod("call", null)
            // TODO: Location
            ?: throw EvalError("Cannot assign $givenType to a function type template, no `call` method", null)

          when (val signature = callMethod.signature()) {
            is Signature.Concrete -> Pair(signature.argTypes, signature.returnType)

            // TODO: Location
            is Signature.Any -> throw EvalError("Cannot assign Any signature to function type template", null)
          }
        }

        // TODO: Location
        else -> throw EvalError("Cannot assign $givenType to a function type template", null)
      }

      if (givenArguments.size != params.size) {
        // TODO: Location
        throw EvalError("Different argument counts. Expected ${params.size}, given ${givenArguments.size}", null)
      }

      val parameterTypes = params.zip(givenArguments).map { (expected, given) ->
        Pair(expected.first, evaluatePattern(frame, partialContext, expected.second, given.second))
      }

      val returnType = evaluatePattern(frame, partialContext, returnType, givenReturnType)

      return PhotonFunctionalInterface(parameterTypes, returnType)
    }

    private fun evaluatePattern(
      frame: VirtualFrame,
      partialContext: PartialContext,
      expected: PatternNode,
      given: Type
    ): Type {
      frame.setAuxiliarySlot(0, LiteralNode(given, RootType, null))
      frame.setObject(0, given)

      val partialResult = expected.executePartial(frame, partialContext)

      // TODO: Verify it's actually a Type
      return partialResult.executeCompileTimeOnly(frame) as Type
    }
  }
}
