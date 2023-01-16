package photon.compiler.values

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.compiler.types.IntType
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class ClassBuilder(
  val name: String,
  val builderClosure: Closure,
  val isInterface: Boolean
) {
  data class Property(val name: String, val type: Type)
  data class Function(val name: String, val function: Closure)

  @ExportMessage
  fun type() = ClassBuilderType

  internal var _properties = mutableListOf<Property>()
  internal var _functions = mutableListOf<Function>()

  val properties: List<Property>
    get() {
      build()
      return _properties
    }

  val functions: List<Function>
    get() {
      build()
      return _functions
    }

  val builtClass by lazy { PhotonClass(this) }
  val builtInterface by lazy { PhotonInterface(this) }

  @CompilationFinal
  private var alreadyBuilt = false

  private fun build() {
    if (alreadyBuilt) {
      return
    }
    alreadyBuilt = true

    val builderMethod = builderClosure.function.type.getMethod("call")
      ?: throw EvalError("Class builder must be callable", null)

    builderMethod.call(
      EvalMode.CompileTimeOnly,
      builderClosure,
      this
    )
  }
}

object ClassBuilderType: Type() {
  override val methods: Map<String, Method> = mapOf(
    Pair("define", DefineMethod),
    Pair("selfType", SelfTypeMethod)
  )

  object DefineMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(IntType)
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val builder = target as ClassBuilder
      val name = args[0] as String

      when (val typeOrFunction = args[1]) {
        is Type -> builder._properties.add(ClassBuilder.Property(name, typeOrFunction))
        is Closure -> builder._functions.add(ClassBuilder.Function(name, typeOrFunction))

        else -> throw EvalError("Cannot pass ${args[1]} to `define`", null)
      }

      // TODO: Return a "Nothing" value
      return 42
    }
  }

  object SelfTypeMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(RootType)
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val builder = target as ClassBuilder

      return if (builder.isInterface)
        builder.builtInterface
      else
        builder.builtClass
    }
  }
}
