package photon.compiler.types

import photon.compiler.core.*
import photon.compiler.values.Closure

class FunctionType: Type() {
  override val methods = mapOf(
    Pair("call", CallMethod)
  )

  object CallMethod: Method(MethodType.Default) {
    // TODO: Actual return type
    override fun signature() = Signature.Any(AnyStatic)

    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val closure = target as Closure

      return closure.function.call(closure.captures, *args)
    }
  }
}