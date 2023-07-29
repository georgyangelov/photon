package photon.compiler.types

import photon.compiler.core.*

object AnyType: Type() {
  override fun type() = AnyType

  // This is handled by the `CallMethod`
  override val methods = emptyMap<String, Method>()
}