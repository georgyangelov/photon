package photon.compiler.types

import photon.compiler.core.*
import photon.core.EvalError

class TemplateFunctionType(val function: PhotonTemplateFunction): Type() {
  override val methods = emptyMap<String, Method>()

  override fun getMethod(name: String, argTypes: List<Type>?): Method? {
    if (name == "call") {
      if (argTypes == null) {
        throw EvalError("Cannot get `call` method of a template function without providing expected types", null)
      }

      val concreteFunction = function.specialize(argTypes)

      return concreteFunction.type.getMethod("call", argTypes)
    }

    return super.getMethod(name, argTypes)
  }
}