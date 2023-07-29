package photon.compiler.types

import photon.compiler.core.*
import photon.compiler.values.*
import photon.compiler.types.classes.ClassBuilder
import kotlin.Any

object ClassObjectType: Type() {
  override val methods: Map<String, Method> = mapOf(
    Pair("new", NewMethod)
  )

  object NewMethod: Method(MethodType.Partial) {
    override fun signature(): Signature = Signature.Any(AnyStatic)

    override fun call(target: Any, vararg args: Any): Any {
      val (name, builderClosure) = when (val arg0 = args[0]) {
        is String -> Pair(arg0, args[1] as Closure)
        else -> Pair(null, arg0 as Closure)
      }

      val classBuilder = ClassBuilder(name, builderClosure, ClassBuilder.Type.Class)

      return classBuilder.result
    }
  }
}
