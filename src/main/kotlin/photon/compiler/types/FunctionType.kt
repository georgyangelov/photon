package photon.compiler.types

import photon.compiler.FrameTools
import photon.compiler.core.*
import photon.compiler.values.Closure

class FunctionType(val function: PhotonFunction): Type() {
  override val methods: Map<String, Method> by lazy {
    function.resolveSignatureTypesWithInference()

    val signature = Signature.Concrete(function.actualArgumentTypes!!, function.actualReturnType!!)

    val methodType =
      if (function.isCompileTimeOnly) MethodType.Partial
      else MethodType.Default

    mapOf(
      Pair("call", CallMethod(function, signature, methodType))
    )
  }

  class CallMethod(
    private val function: PhotonFunction,
    private val signature: Signature,
    methodType: MethodType
  ): Method(methodType) {
    override fun signature() = signature

    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val closure = target as Closure
      val captures = FrameTools.captureValues(closure.capturedFrame, function.requiredCaptures)

      return function.call(captures, *args)
    }
  }
}