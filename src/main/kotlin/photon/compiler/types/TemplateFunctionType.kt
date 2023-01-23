package photon.compiler.types

import photon.compiler.core.*

class TemplateFunctionType(function: PhotonTemplateFunction): Type() {
  override val methods = mapOf(
    Pair("call", CallMethod)
  )

  class CallMethod(
    private val function: PhotonTemplateFunction,
    methodType: MethodType
  ): Method(methodType) {
    private val methodSignature by lazy {
      Signature.
    }

    override fun signature() = methodSignature
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      TODO("Not yet implemented")
    }
  }
}