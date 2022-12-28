package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import java.lang.reflect.Method

abstract class Type: Value() {
  abstract val methods: Map<String, Method>
}

object RootType: Type() {
  override fun typeOf(frame: VirtualFrame): Type = this
  override fun executeGeneric(frame: VirtualFrame): Any = this

  override val methods: Map<String, Method> = emptyMap()
}
