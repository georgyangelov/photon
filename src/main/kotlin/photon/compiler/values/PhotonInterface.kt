package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary
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

class ConvertorMethod(
  val signature: Signature,
  val from: Method,
  val conversion: CallConversion
): Method(from.type) {
  override fun signature(): Signature = signature

  // TODO: @ExplodeLoop
  @Suppress("UNCHECKED_CAST")
  override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
    val convertedArgs = arrayOfNulls<Any>(args.size)

    for (i in convertedArgs.indices) {
      convertedArgs[i] = conversion.argumentConversions[i](args[i])
    }

    val result = from.call(evalMode, target, *(convertedArgs as Array<Any>))
    val convertedResult = conversion.returnConversion(result)

    return convertedResult
  }
}

@ExportLibrary(ValueLibrary::class)
class PhotonInterface(
  val builder: ClassBuilder
): Interface() {
  @ExportMessage
  fun type() = PhotonInterfaceType(this, builder)

  override fun conversionFrom(other: Type): PossibleTypeError<ValueConvertor> {
    val methodTable = methods.map { (name, interfaceMethod) ->
      val valueMethod = other.getMethod(name)
        ?: // TODO: Location
        return PossibleTypeError.Error(TypeError("Type $other does not have a method named $name", null))

      val interfaceMethodSignature = interfaceMethod.signature()
      val valueMethodSignature = valueMethod.signature()

      val conversion = when (val result = interfaceMethodSignature.assignableFrom(valueMethodSignature)) {
        is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error)
        is PossibleTypeError.Success -> result.value
      }

      Pair(name, ConvertorMethod(interfaceMethodSignature, valueMethod, conversion))
    }.toMap()

    return PossibleTypeError.Success { PhotonInterfaceInstance(this, it, methodTable) }
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
  // TODO: Somehow unify with the other class for interfaces?
  override fun conversionFrom(other: Type): PossibleTypeError<ValueConvertor> {
    val originalMethod = other.getMethod("call")
      ?: // TODO: Location
      return PossibleTypeError.Error(TypeError("Type $other does not have a method named call", null))

    val interfaceMethodSignature = Signature.Concrete(parameters, returnType)
    val valueMethodSignature = originalMethod.signature()

    val conversion = when (val result = interfaceMethodSignature.assignableFrom(valueMethodSignature)) {
      is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error)
      is PossibleTypeError.Success -> result.value
    }

    val convertorMethod = ConvertorMethod(interfaceMethodSignature, originalMethod, conversion)

    val methodTable = mapOf(
      Pair("call", convertorMethod)
    )

    return PossibleTypeError.Success { PhotonInterfaceInstance(this, it, methodTable) }
  }

  // TODO: Somehow unify with the other class for interfaces?
  override val methods: Map<String, Method> = mapOf(
    Pair("call", object: Method(MethodType.Default) {
      override fun signature(): Signature = Signature.Concrete(parameters, returnType)
      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        val self = target as PhotonInterfaceInstance

        val method = self.methodTable["call"]!!

        return method.call(evalMode, self.value, *args)
      }
    })
  )
}