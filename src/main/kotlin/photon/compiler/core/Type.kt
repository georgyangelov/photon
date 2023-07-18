package photon.compiler.core

abstract class Type: PhotonObject(null) {
  abstract val methods: Map<String, Method>

  override fun type(): Type = RootType

  open fun getMethod(name: String, argTypes: List<Type>?): Method? = methods[name]
}

object RootType: Type() {
  override val methods: Map<String, Method> = emptyMap()
}

object AnyStatic: Type() {
  override val methods: Map<String, Method> = emptyMap()
}