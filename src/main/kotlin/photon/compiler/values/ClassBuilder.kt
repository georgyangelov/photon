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
  val name: String?,
  private val builderClosure: Closure,
  val type: BuildType,
  private val instanceBuilder: ClassBuilder? = null
) {
  enum class BuildType {
    Class,
    Interface
  }

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

  val builtValue: Type by lazy {
    when (type) {
      BuildType.Class -> PhotonClass(
        this,
        staticInstanceFor =
          if (instanceBuilder != null) instanceBuilder.builtValue as PhotonClass
          else null
      )
      BuildType.Interface -> PhotonInterface(this)
    }
  }

  val staticBuiltValue by lazy {
    build()

    if (instanceBuilder != null) {
      // We are the static builder
      RootType
    } else if (staticBuilder != null) {
      // We are the instance builder and there are static definitions
      staticBuilder!!.builtValue
    } else {
      // We are the instance builder and there are no static definitions
      when (type) {
        BuildType.Class -> PhotonClassType(builtValue as PhotonClass)
        BuildType.Interface -> RootType
      }
    }
  }

  @CompilationFinal
  private var alreadyBuilt = false

  var staticBuilder: ClassBuilder? = null

  fun defineStaticType(builderClosure: Closure): ClassBuilder {
    if (staticBuilder == null) {
      staticBuilder = ClassBuilder(
        name?.plus("$"),
        builderClosure,
        type = type,
        instanceBuilder = this
      )
    }

    return staticBuilder!!
  }

  private fun build() {
    if (alreadyBuilt) {
      return
    }
    alreadyBuilt = true

    val builderMethod = builderClosure.type().getMethod("call", null)
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
    Pair("selfType", SelfTypeMethod),
    Pair("static", StaticMethod)
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

      return builder.builtValue
    }
  }

  object StaticMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(IntType)
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val builder = target as ClassBuilder
      val closure = args[0] as Closure

      builder.defineStaticType(closure)

      // TODO: Return a "Nothing" value
      return 42
    }
  }
}
