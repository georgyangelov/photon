package photon.compiler.types.classes

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.PhotonValueLibrary
import photon.compiler.types.IntType
import photon.compiler.values.*
import photon.core.EvalError

class ClassBuilder(
  name: String?,
  definitionClosure: Closure,
  private val type: Type
) {
  enum class Type {
    Class,
    // TODO: Support objects directly
    // Object,
    Interface
  }

  val instanceDefinitions = Definitions(definitionClosure, this, isForStaticType = false)
  var staticDefinitions: Definitions? = null

  val metaClass by lazy {
    PhotonClass(
      name = name,
      properties = lazy {
        // This calls the closure which defines staticDefinitions
        instanceDefinitions.build()

        val staticDefs = staticDefinitions

        staticDefs?.build()
        staticDefs?.properties ?: emptyList()
      },
      functions = lazy {
        // This calls the closure which defines staticDefinitions
        instanceDefinitions.build()

        val staticDefs = staticDefinitions

        staticDefs?.build()
        staticDefs?.functions ?: emptyList()
      },
      metaClass = lazyOf(RootType),
      canCreateInstancesOf =
        if (type == Type.Class)
          lazy { result as PhotonClass }
        else null
    )
  }

  val result: photon.compiler.core.Type by lazy {
    when (type) {
      Type.Class -> PhotonClass(
        name = name,
        properties = lazy {
          instanceDefinitions.build()
          instanceDefinitions.properties
        },
        functions = lazy {
          instanceDefinitions.build()
          instanceDefinitions.functions
        },
        metaClass = lazy { metaClass }
      )

      Type.Interface -> PhotonInterface(
        name = name,
        properties = lazy {
          instanceDefinitions.build()
          instanceDefinitions.properties
        },
        functions = lazy {
          instanceDefinitions.build()
          instanceDefinitions.functions
        },
        metaClass = lazy { metaClass }
      )
    }
  }
}

@ExportLibrary(PhotonValueLibrary::class)
class Definitions(
  val closure: Closure,
  val builder: ClassBuilder,
  val isForStaticType: Boolean
) {
  data class Property(val name: String, val type: Type)
  data class Function(val name: String, val function: Closure)

  val properties = mutableListOf<Property>()
  val functions = mutableListOf<Function>()

  @ExportMessage
  fun isPhotonValue() = true

  @ExportMessage
  fun type() = DefinitionsType

  @CompilerDirectives.CompilationFinal
  private var alreadyBuilt = false

  fun build() {
    if (alreadyBuilt) {
      return
    }
    alreadyBuilt = true

    val builderMethod = closure.type().getMethod("call", null)
      ?: throw EvalError("Class builder must be callable", null)

    builderMethod.call(closure, this)
  }
}

object DefinitionsType: Type() {
  override val methods = mapOf(
    Pair("define", DefineMethod),
    Pair("selfType", SelfTypeMethod),
    Pair("static", StaticMethod)
  )

  object DefineMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(IntType)
    override fun call(target: Any, vararg args: Any): Any {
      val definitions = target as Definitions
      val name = args[0] as String

      when (val typeOrFunction = args[1]) {
        is Type -> definitions.properties.add(Definitions.Property(name, typeOrFunction))
        is Closure -> definitions.functions.add(Definitions.Function(name, typeOrFunction))

        else -> throw EvalError("Cannot pass ${args[1]} to `define`", null)
      }

      // TODO: Return a "Nothing" value
      return 42
    }
  }

  object SelfTypeMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(RootType)
    override fun call(target: Any, vararg args: Any): Any {
      val definitions = target as Definitions

      return if (definitions.isForStaticType) {
        definitions.builder.metaClass
      } else {
        definitions.builder.result
      }
    }
  }

  object StaticMethod: Method(MethodType.CompileTimeOnly) {
    override fun signature(): Signature = Signature.Any(IntType)
    override fun call(target: Any, vararg args: Any): Any {
      val definitions = target as Definitions
      val closure = args[0] as Closure

      if (definitions.isForStaticType) {
        // TODO: Location
        throw EvalError("Cannot define static methods on the static object", null)
      }

      definitions.builder.staticDefinitions = Definitions(closure, definitions.builder, isForStaticType = true)

      // TODO: Return a "Nothing" value
      return 42
    }
  }
}