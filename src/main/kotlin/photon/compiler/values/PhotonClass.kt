package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.staticobject.*
import photon.compiler.PhotonContext
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class PhotonClass(
  private val builder: ClassBuilder
): Type() {
  @ExportMessage
  fun type() = PhotonClassType(this, builder)

  internal val instanceType by lazy {
    PhotonClassInstanceType(builder)
  }

  override val methods: Map<String, Method> by lazy {
    instanceType.methods
  }
}

class PhotonClassType(
  val klass: PhotonClass,
  val builder: ClassBuilder
): Type() {
  override val methods: Map<String, Method> by lazy {
    mapOf(
      // Person.new
      Pair("new", object: Method(MethodType.Default) {
        override fun signature(): Signature {
          return Signature.Concrete(
            builder.properties.map { Pair(it.name, it.type) },
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
      })
    )
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

  override val methods: Map<String, Method> by lazy {
    val getters = builder.properties
      .zip(shapeProperties)
      .associate { Pair(it.first.name, methodForProperty(it.first, it.second)) }

    val functions = builder.functions
      .associate { Pair(it.name, methodForFunction(it)) }

    getters + functions
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
    return object: Method(MethodType.Default) {
      private val callMethod by lazy {
        function.function.type().getMethod("call")
          ?: throw EvalError("Function is not callable", null)
      }

      override fun signature() = callMethod.signature().withoutSelfArgument()

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        val shouldPassSelf = callMethod.signature().hasSelfArgument()

        if (shouldPassSelf) {
          return callMethod.call(evalMode, function.function, target, *args)
        } else {
          return callMethod.call(evalMode, function.function, *args)
        }
      }
    }
  }
}