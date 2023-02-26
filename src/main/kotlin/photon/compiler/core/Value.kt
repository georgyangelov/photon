package photon.compiler.core

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import photon.compiler.PartialContext

sealed class EvalMode {
  // Running compile-time-only code
  object CompileTimeOnly: EvalMode()

  // Partially evaluating code in a default function
  object Partial: EvalMode()

  // Partially evaluating code in a runtime-only function
  object PartialRunTimeOnly: EvalMode()

  // Partially evaluating code in a prefer-runtime function
  object PartialPreferRunTime: EvalMode()

  // Running code during runtime
  object RunTime: EvalMode()
}

class CouldNotFullyEvaluateInCompileTimeOnlyMode: Exception()

abstract class PhotonNode: Node() {
  companion object {
    val identityWrapper: NodeWrapper = { node -> node }
  }

  abstract val type: Type

  /**
   * Evaluates in the compile-time-only mode.
   *
   * This should fully evaluate everything or result in a CouldNotFullyEvaluateInCompileTimeOnlyMode
   * exception.
   *
   * The result here should never be a Value, unless the actually produced value
   * was a Value.
   */
  abstract fun executeCompileTimeOnly(frame: VirtualFrame): Any

  /**
   * Evaluates in the partial mode. This has a side effect of populating
   * the types of this and all children Operation nodes.
   *
   * The result should be a Value.
   */
  abstract fun executePartial(frame: VirtualFrame, context: PartialContext): PhotonNode

//  abstract fun evaluateRuntime(frame: VirtualFrame): Any

  open fun isOperation(): Boolean = false

  abstract fun canBeCapturedDuringPartialEvaluation(frame: VirtualFrame): Boolean
}

abstract class OperationNode: PhotonNode() {
  @CompilerDirectives.CompilationFinal
  private lateinit var _type: Type

  override var type: Type
    get() = _type
    protected set(value) {
      CompilerDirectives.interpreterOnly { _type = value }
    }

  override fun isOperation(): Boolean = true
}

typealias NodeWrapper = (PhotonNode) -> PhotonNode