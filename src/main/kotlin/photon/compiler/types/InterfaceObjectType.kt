package photon.compiler.types

import photon.compiler.core.*
import photon.compiler.values.classes.ClassBuilder
import photon.compiler.values.Closure

object InterfaceObjectType: Type() {
  override val methods: Map<String, Method> = mapOf(
    Pair("new", NewMethod)
  )

  object NewMethod: Method(MethodType.Partial) {
    override fun signature(): Signature = Signature.Any(AnyStatic)

    override fun call(target: Any, vararg args: Any): Any {
      val name = args[0] as String
      val builderClosure = args[1] as Closure
      val classBuilder = ClassBuilder(name, builderClosure, ClassBuilder.Type.Interface)

      return classBuilder.result
    }
  }
}
