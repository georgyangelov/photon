package photon.compiler.types

import photon.compiler.core.*
import photon.compiler.values.ClassBuilder
import photon.compiler.values.Closure

object ClassObjectType: Type() {
  override val methods: Map<String, Method> = mapOf(
    Pair("new", NewMethod)
  )

  object NewMethod: Method(MethodType.Partial) {
    override fun signature(): Signature = Signature.Any(AnyStatic)

    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val name = args[0] as String
      val builderClosure = args[1] as Closure

      val builder = ClassBuilder(name)

      builderClosure.function.call(
        builderClosure.captures,
        builder
      )

      return builder.build()
    }
  }
}
