package photon.compiler.types

import photon.compiler.core.*

object IntType: Type() {
  override val methods = mapOf(
    Pair("+", PlusMethod)
  )

  object PlusMethod: Method.Default() {
    override val signature: Signature = Signature.Concrete(
      listOf(Pair("other", IntType)),
      IntType
    )

    @Suppress("unused")
    fun call(a: PObject<Int>, b: PObject<Int>): PObject<Int> {
      return PObject(a.value + b.value, IntType)
    }
  }
}