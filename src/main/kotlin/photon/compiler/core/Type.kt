package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame

abstract class Type: Value() {
  abstract val methods: Map<String, Method>

  override fun isOperation(): Boolean = false
  override fun typeOf(frame: VirtualFrame): Type = RootType
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any = this
}

object RootType: Type() {
  override fun typeOf(frame: VirtualFrame): Type = this
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any = this

  override val methods: Map<String, Method> = emptyMap()
}

object AnyStatic: Type() {
  override val methods: Map<String, Method> = emptyMap()
}