package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.staticobject.*
import photon.compiler.PhotonContext
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.compiler.types.TemplateFunctionType
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class PhotonClass(
  private val builder: ClassBuilder,
  private val staticInstanceFor: PhotonClass? = null
): Type() {
  @ExportMessage
  fun type(): Type = builder.staticBuiltValue

  internal val properties
    get() = builder.properties

  internal val instanceType by lazy {
    PhotonClassInstanceType(builder)
  }

  override val methods = emptyMap<String, Method>()

  override fun getMethod(name: String, argTypes: List<Type>?): Method? {
    // TODO: Move to `methods`
    if (staticInstanceFor != null && name == "new") return NewObjectMethod(staticInstanceFor)

    return instanceType.getMethod(name, argTypes)
  }
}

class PhotonClassType(
  val klass: PhotonClass
): Type() {
  override val methods: Map<String, Method> by lazy {
    mapOf(
      // Person.new
      Pair("new", NewObjectMethod(klass))
    )
  }
}

class NewObjectMethod(
  private val klass: PhotonClass
): Method(MethodType.Default) {
  override fun signature(): Signature {
    return Signature.Concrete(
      klass.properties.map { Pair(it.name, it.type) },
      klass
    )
  }

  override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
    // TODO: `AllocationReporter` from `SLNewObjectBuiltin`
    val newObject = klass.instanceType.shape.factory.create()

    klass.instanceType.shapeProperties.withIndex().forEach {
      // TODO: Type-check
      it.value.setObject(newObject, args[it.index])
    }

    return newObject
  }
}

abstract class ObjectInstance

interface PhotonStaticObjectFactory {
  fun create(): Any
}

// person = Person.new
class PhotonClassInstanceType(val builder: ClassBuilder): Type() {
  internal val shapeProperties by lazy {
    builder.properties.map { DefaultStaticProperty(it.name) }
  }

  internal val shape by lazy {
    val shapeBuilder = StaticShape.newBuilder(
      PhotonContext.current().language,
    )

    shapeProperties.forEach {
      shapeBuilder.property(it, java.lang.Object::class.java, true)
    }

    shapeBuilder.build(
      ObjectInstance::class.java,
      PhotonStaticObjectFactory::class.java
    )
  }

  val templateFunctions by lazy {
    builder.functions
      .filter { it.function._type is TemplateFunctionType }
      .associate { Pair(it.name, it.function) }
  }

  override val methods: Map<String, Method> by lazy {
    val getters = builder.properties
      .zip(shapeProperties)
      .associate { Pair(it.first.name, methodForProperty(it.first, it.second)) }

    val functions = builder.functions
      .filterNot { it.function._type is TemplateFunctionType }
      .associate { Pair(it.name, methodForFunction(it)) }

    getters + functions
  }

  override fun getMethod(name: String, argTypes: List<Type>?): Method? {
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

  private fun methodForProperty(property: ClassBuilder.Property, shapeProperty: StaticProperty): Method {
    return object: Method(MethodType.Default) {
      override fun signature() = Signature.Concrete(emptyList(), property.type)

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        return shapeProperty.getObject(target)
      }
    }
  }

  private fun methodForFunction(function: ClassBuilder.Function): Method {
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

      if (shouldPassSelf) {
        return method.call(evalMode, self, target, *args)
      } else {
        return method.call(evalMode, self, *args)
      }
    }
  }
}