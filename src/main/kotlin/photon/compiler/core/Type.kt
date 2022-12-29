package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.libraries.TypeLibrary

@ExportLibrary(TypeLibrary::class)
abstract class Type: Value() {
  abstract val methods: Map<String, Method>

  @ExportMessage
  fun getMethod(name: String): Method? = methods[name]

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