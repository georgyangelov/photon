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

  override val type: Type
    get() = RootType

  override fun executePartial(frame: PartialFrame, evalMode: EvalMode): Value {
    return this
  }

  override fun executeCompileTimeOnly(frame: VirtualFrame): Any = this
}

object RootType: Type() {
  override val methods: Map<String, Method> = emptyMap()
}

object AnyStatic: Type() {
  override val methods: Map<String, Method> = emptyMap()
}