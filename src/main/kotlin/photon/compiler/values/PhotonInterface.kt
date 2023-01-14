package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
import photon.compiler.nodes.ConvertToInterfaceNode
import photon.core.TypeError

@ExportLibrary(ValueLibrary::class)
class PhotonInterfaceInstance(
  val iface: Type,
  val value: Any,
  val methodTable: Map<String, Method>
) {
  @ExportMessage
  fun type() = iface
}

@ExportLibrary(ValueLibrary::class)
class PhotonInterface(
  val builder: ClassBuilder
): Interface() {
  @ExportMessage
  fun type() = PhotonInterfaceType(this, builder)

  override fun conversionFrom(other: Type): PossibleTypeError<NodeWrapper> {
    val methodTable = methods.map { (name, interfaceMethod) ->
      val valueMethod = other.getMethod(name)
        ?: // TODO: Location
        return PossibleTypeError.Error(TypeError("Type $other does not have a method named $name", null))

      val interfaceMethodSignature = interfaceMethod.signature()
      val valueMethodSignature = valueMethod.signature()

      when (val result = interfaceMethodSignature.assignableFrom(valueMethodSignature)) {
        is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error)
        is PossibleTypeError.Success -> {}
      }

      Pair(name, valueMethod)
    }.toMap()

    return PossibleTypeError.Success { value -> ConvertToInterfaceNode(other, methodTable, value) }
  }

  // TODO: Support methods defined directly on the interface
  override val methods: Map<String, Method> by lazy {
    builder.properties.associate { methodForVirtualFunction(it) }
  }

  private fun methodForVirtualFunction(property: ClassBuilder.Property): Pair<String, Method> {
    // TODO: Support other method types?
    val method = object: Method(MethodType.Default) {
      override fun signature(): Signature {
        val type = property.type

        return if (type is PhotonFunctionalInterface) {
          Signature.Concrete(type.parameters, type.returnType)
        } else {
          // Property getter
          Signature.Concrete(emptyList(), type)
        }
      }

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        val self = target as PhotonInterfaceInstance
        val method = self.methodTable[property.name]!!

        return method.call(evalMode, self.value, *args)
      }
    }

    return Pair(property.name, method)
  }
}

class PhotonInterfaceType(
  val iface: PhotonInterface,
  val builder: ClassBuilder
): Type() {
  override val methods: Map<String, Method> = emptyMap()
}

class PhotonFunctionalInterface(
  val parameters: List<Pair<String, Type>>,
  val returnType: Type
): Interface() {
  override fun conversionFrom(other: Type): PossibleTypeError<NodeWrapper> {
    TODO("Not yet implemented")
  }

  override val methods: Map<String, Method>
    get() = TODO("Not yet implemented")
}