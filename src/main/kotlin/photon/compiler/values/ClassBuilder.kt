package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.compiler.types.IntType
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class ClassBuilder(val name: String) {
  val properties = mutableListOf<PhotonClass.Property>()
  val functions = mutableListOf<PhotonClass.Function>()

  @ExportMessage
  fun type() = ClassBuilderType

  fun build() = PhotonClass(name, properties.toTypedArray(), functions.toTypedArray())
}

object ClassBuilderType: Type() {
  override val methods: Map<String, Method> = mapOf(
    Pair("define", DefineMethod)
  )

  object DefineMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(IntType)
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val builder = target as ClassBuilder
      val name = args[0] as String

      when (val typeOrFunction = args[1]) {
        is Type -> builder.properties.add(PhotonClass.Property(name, typeOrFunction))
        is Closure -> builder.functions.add(PhotonClass.Function(name, typeOrFunction))

        else -> throw EvalError("Cannot pass ${args[1]} to `define`", null)
      }

      // TODO: Return a "Nothing" value
      return 42
    }
  }
}
