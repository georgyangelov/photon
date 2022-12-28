package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

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

abstract class Value: Node() {
  // TODO: Probably need to use a Library for these for performance
  abstract fun typeOf(frame: VirtualFrame): Type
  abstract fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any
  abstract fun isOperation(): Boolean
}

typealias ValueWrapper = (Value) -> Value