package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.staticobject.*
import photon.compiler.PhotonContext
import photon.compiler.PhotonLanguage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.core.EvalError

@ExportLibrary(ValueLibrary::class)
class PhotonClass(
  val name: String,
  properties: Array<Property>,
  functions: Array<Function>
) {
  private val type: Type = PhotonClassType(properties, functions)

  @ExportMessage
  fun type() = type

  data class Property(val name: String, val type: Type)
  data class Function(val name: String, val function: Closure)
}

class PhotonClassType(
  val properties: Array<PhotonClass.Property>,
  val functions: Array<PhotonClass.Function>
): Type() {
  val instanceType = PhotonClassInstanceType(this)

  override val methods: Map<String, Method> = mapOf(
    Pair("new", object: Method(MethodType.CompileTimeOnly) {
      override fun signature() = Signature.Concrete(
        properties.map { Pair(it.name, it.type) },
        instanceType
      )

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        // TODO: `AllocationReporter` from `SLNewObjectBuiltin`
        val newObject = instanceType.shape.factory.create()

        instanceType.shapeProperties.withIndex().forEach {
          // TODO: Type-check
          it.value.setObject(newObject, args[it.index])
        }

        return newObject
      }
    })
  )
}

abstract class ObjectInstance {}

interface PhotonStaticObjectFactory {
  fun create(): Any
}

class PhotonClassInstanceType(
  classType: PhotonClassType
): Type() {
  val shapeProperties = classType.properties.map { DefaultStaticProperty(it.name) }

  val shape: StaticShape<PhotonStaticObjectFactory> = StaticShape.newBuilder(
    PhotonContext.current().language,
  ).apply {
    shapeProperties.forEach { property(it, java.lang.Object::class.java, true) }
  }.build(
    ObjectInstance::class.java,
    PhotonStaticObjectFactory::class.java
  )

  override val methods: Map<String, Method> =
    classType.properties
      .zip(shapeProperties)
      .associate { Pair(it.first.name, methodForProperty(it.first, it.second)) } +
    classType.functions.associate { Pair(it.name, methodForFunction(it)) }

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

    // TODO: Use MethodType based on the function itself
    return object: Method(MethodType.Default) {
      override fun signature(): Signature = callMethod.signature()

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        // TODO: Self argument
        return callMethod.call(evalMode, function.function, *args)
      }
    }
  }
}
