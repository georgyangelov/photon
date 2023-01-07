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
  val name: String,

  // TODO: Unset this and make it CompileTimeConstant
  val builderClosure: Closure
) {
  // TODO: CompileTimeConstant
  internal var properties: Array<Property>? = null
  internal var functions: Array<Function>? = null

  private val type: Type = PhotonClassType(this)
  internal val instanceType by lazy {
    build()
    PhotonClassInstanceType(this)
  }

  @ExportMessage
  fun type() = type

  fun build() {
    if (properties != null && functions != null) {
      return
    }

    val builder = ClassBuilder(name)

    builderClosure.function.call(
      builderClosure.captures,
      builder
    )

    properties = builder.properties.toTypedArray()
    functions = builder.functions.toTypedArray()
  }

  data class Property(val name: String, val type: Type)
  data class Function(val name: String, val function: Closure)
}

class PhotonClassType(klass: PhotonClass): Type() {
  override val methods: Map<String, Method> by lazy {
    mapOf(
      Pair("new", object: Method(MethodType.CompileTimeOnly) {
        override fun signature(): Signature {
          klass.build()
          val properties = klass.properties!!

          return Signature.Concrete(
            properties.map { Pair(it.name, it.type) },
            klass.instanceType
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

class PhotonClassInstanceType(
  klass: PhotonClass
): Type() {
  val shapeProperties = klass.properties!!.map { DefaultStaticProperty(it.name) }

  val shape: StaticShape<PhotonStaticObjectFactory> = StaticShape.newBuilder(
    PhotonContext.current().language,
  ).apply {
    shapeProperties.forEach { property(it, java.lang.Object::class.java, true) }
  }.build(
    ObjectInstance::class.java,
    PhotonStaticObjectFactory::class.java
  )

  override val methods: Map<String, Method> =
    klass.properties!!
      .zip(shapeProperties)
      .associate { Pair(it.first.name, methodForProperty(it.first, it.second)) } +
    klass.functions!!.associate { Pair(it.name, methodForFunction(it)) }

  private fun methodForProperty(property: PhotonClass.Property, shapeProperty: StaticProperty): Method {
    return object: Method(MethodType.Default) {
      override fun signature() = Signature.Concrete(emptyList(), property.type)

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        return shapeProperty.getObject(target)
      }
    }
  }

  private fun methodForFunction(function: PhotonClass.Function): Method {
    val callMethod = function.function.type().getMethod("call")
      ?: throw EvalError("Function is not callable", null)

    val closureSignature = callMethod.signature()
    val methodSignature = closureSignature.withoutSelfArgument()

    // TODO: @CompileTimeStatic
    val shouldPassSelf = closureSignature.hasSelfArgument()

    // TODO: Use MethodType based on the function itself
    return object: Method(MethodType.Default) {
      override fun signature(): Signature = methodSignature

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        if (shouldPassSelf) {
          return callMethod.call(evalMode, function.function, target, *args)
        } else {
          return callMethod.call(evalMode, function.function, *args)
        }
      }
    }
  }
}
