package photon.compiler.types

import photon.compiler.core.*
import photon.compiler.values.Closure

class FunctionType(function: PhotonFunction): Type() {
  override val methods: Map<String, Method> by lazy {
    function.resolveArgumentTypes()
    function.resolveReturnType()

    val signature = Signature.Concrete(function.actualArgumentTypes!!, function.actualReturnType!!)

    mapOf(
      Pair("call", CallMethod(signature))
    )
  }

  class CallMethod(private val signature: Signature): Method(MethodType.Default) {
    override fun signature() = signature

    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val closure = target as Closure

      return closure.function.call(closure.captures, *args)
    }
  }
}