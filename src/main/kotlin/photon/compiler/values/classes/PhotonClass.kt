package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.staticobject.*
import photon.compiler.PhotonContext
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.compiler.types.TemplateFunctionType
import photon.compiler.values.classes.*
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class PhotonClass(
  val name: String?,
  internal val properties: Lazy<List<Definitions.Property>>,
  internal val functions: Lazy<List<Definitions.Function>>,
  internal val metaClass: Lazy<Type>,
  private val canCreateInstancesOf: Lazy<PhotonClass>? = null
): Type() {
  @ExportMessage
  fun type(): Type = metaClass.value

  val shapeProperties by lazy {
    properties.value.map { DefaultStaticProperty(it.name) }
  }

  val shape: StaticShape<PhotonStaticObjectFactory> by lazy {
    val shapeBuilder = StaticShape.newBuilder(
      PhotonContext.current().language,
    )

    shapeProperties.forEach {
      shapeBuilder.property(it, java.lang.Object::class.java, true)
    }

    shapeBuilder.build(
      PhotonClassInstance::class.java,
      PhotonStaticObjectFactory::class.java
    )
  }

  private val templateFunctions by lazy {
    functions.value
      .filter { it.function._type is TemplateFunctionType }
      .associate { Pair(it.name, it.function) }
  }

  override val methods: Map<String, Method> by lazy {
    val getters = properties.value
      .zip(shapeProperties)
      .associate { Pair(it.first.name, methodForProperty(it.first, it.second)) }

    val functions = functions.value
      .filterNot { it.function._type is TemplateFunctionType }
      .associate { Pair(it.name, methodForFunction(it)) }

    val newMethod = if (canCreateInstancesOf != null) {
      mapOf(Pair("new", NewObjectMethod(canCreateInstancesOf.value)))
    } else emptyMap<String, Method>()

    getters + functions + newMethod
  }

  override fun getMethod(name: String, argTypes: List<Type>?): Method? {
    // TODO: Move to `methods`
    // if (staticInstanceFor != null && name == "new") return NewObjectMethod(staticInstanceFor)
    //
    // return instanceType.getMethod(name, argTypes)

    val templateFunctionClosure = templateFunctions[name]
    if (templateFunctionClosure != null) {
      if (argTypes == null) {
        // TODO: Location
        throw EvalError("Cannot specialize template function $name without argTypes", null)
      }

      val callMethod = templateFunctionClosure.type().getMethod("call", listOf(this) + argTypes)
        // TODO: Location
        ?: throw EvalError("Cannot find `call` method on template function", null)

      return SelfPassingMethod(templateFunctionClosure, callMethod)
    }

    return super.getMethod(name, argTypes)
  }

  private fun methodForProperty(property: Definitions.Property, shapeProperty: StaticProperty): Method {
    return object: Method(MethodType.Default) {
      override fun signature() = Signature.Concrete(emptyList(), property.type)

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        return shapeProperty.getObject(target)
      }
    }
  }

  private fun methodForFunction(function: Definitions.Function): Method {
    // TODO: Use MethodType based on the function itself
    // TODO: Cache and instantiate through `getMethod` instead of doing this lazy proxy
    return object: Method(MethodType.Default) {
      private val callMethod by lazy {
        val original = function.function.type().getMethod("call", null)
          ?: throw EvalError("Function is not callable", null)

        SelfPassingMethod(function.function, original)
      }

      override fun signature() = callMethod.signature()

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        return callMethod.call(evalMode, target, *args)
      }
    }
  }

  // TODO: Use this in `methodForFunction` as well?
  class SelfPassingMethod(
    val self: Any,
    val method: Method
  ): Method(method.type) {
    override fun signature(): Signature = method.signature().withoutSelfArgument()
    override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
      val shouldPassSelf = method.signature().hasSelfArgument()

      return if (shouldPassSelf) {
        method.call(evalMode, self, target, *args)
      } else {
        method.call(evalMode, self, *args)
      }
    }
  }
}

class NewObjectMethod(
  private val klass: PhotonClass
): Method(MethodType.Default) {
  override fun signature(): Signature {
    return Signature.Concrete(
      klass.properties.value.map { Pair(it.name, it.type) },
      klass
    )
  }

  override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
    // TODO: `AllocationReporter` from `SLNewObjectBuiltin`
    val newObject = klass.shape.factory.create(klass)

    klass.shapeProperties.withIndex().forEach {
      // TODO: Type-check
      it.value.setObject(newObject, args[it.index])
    }

    return newObject
  }
}