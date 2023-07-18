package photon.compiler.core

abstract class Type(metaType: Type? = null): PhotonObject(metaType) {
  abstract val methods: Map<String, Method>

  open fun getMethod(name: String, argTypes: List<Type>?): Method? = methods[name]
}

object RootType: Type() {
  override val methods: Map<String, Method> = emptyMap()
}

object AnyStatic: Type() {
  override val methods: Map<String, Method> = emptyMap()
}