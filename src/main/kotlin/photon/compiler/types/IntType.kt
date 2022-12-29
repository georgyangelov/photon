package photon.compiler.types

import photon.compiler.core.*

object IntType: Type() {
  override val methods = mapOf(
    Pair("+", PlusMethod)
  )

  object PlusMethod: Method(MethodType.Default) {
    override fun signature(): Signature = Signature.Concrete(
      listOf(Pair("other", IntType)),
      IntType
    )

    @Suppress("UNCHECKED_CAST")
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      return target as Int + args[0] as Int
    }
  }
}