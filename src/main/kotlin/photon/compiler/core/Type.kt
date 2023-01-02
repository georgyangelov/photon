package photon.compiler.core

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.libraries.TypeLibrary

@ExportLibrary(TypeLibrary::class)
abstract class Type {
  abstract val methods: Map<String, Method>

  @ExportMessage
  fun getMethod(name: String): Method? = methods[name]
}

object RootType: Type() {
  override val methods: Map<String, Method> = emptyMap()
}

object AnyStatic: Type() {
  override val methods: Map<String, Method> = emptyMap()
}