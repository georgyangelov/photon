package photon.compiler.values.classes

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.*
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.*
import photon.compiler.libraries.*
import photon.core.EvalError
import photon.core.TypeError

@ExportLibrary(PhotonValueLibrary::class)
@ExportLibrary(InteropLibrary::class)
class PhotonInterfaceInstance(
  val iface: Type,
  val value: Any,
  // TODO: Can this happen without this being a per-instance value?
  val methodTable: Map<String, Method>
): TruffleObject {
  @ExportMessage
  fun isPhotonValue() = true

  @ExportMessage
  fun type() = iface

  @ExportMessage
  fun hasMembers() = true

  @ExportMessage
  fun getMembers(includeInternal: Boolean): Any {
    return iface.methods.keys.sorted()
  }

  @ExportMessage
  fun isMemberInvocable(member: String): Boolean = methodTable.containsKey(member)

  @ExportMessage
  fun invokeMember(member: String, vararg arguments: Any): Any {
    // TODO: Throw error if method is missing
    return methodTable[member]!!.call(value, *arguments)
  }
}

class ConverterMethod(
  val signature: Signature,
  val from: Method,
  val conversion: CallConversion
): Method(from.type) {
  override fun signature(): Signature = signature

  @ExplodeLoop
  @Suppress("UNCHECKED_CAST")
  override fun call(target: Any, vararg args: Any): Any {
    CompilerDirectives.isCompilationConstant(args.size)

    val convertedArgs = arrayOfNulls<Any>(args.size)

    CompilerDirectives.isCompilationConstant(convertedArgs.size)

    for (i in convertedArgs.indices) {
      convertedArgs[i] = conversion.argumentConversions[i](args[i])
    }

    val result = from.call(target, *(convertedArgs as Array<Any>))

    return conversion.returnConversion(result)
  }
}

class PhotonInterface(
  val name: String?,
  val properties: Lazy<List<Definitions.Property>>,
  val functions: Lazy<List<Definitions.Function>>,
  val metaClass: Lazy<Type>
): Interface() {
  override fun type() = metaClass.value

  override fun conversionFrom(other: Type): PossibleTypeError<ValueConverter> {
    val methodTable = virtualMethods.map { (name, interfaceMethod) ->
      val valueMethod = other.getMethod(name, interfaceMethod.argumentTypes)
        ?: // TODO: Location
        return PossibleTypeError.Error(TypeError("Type $other does not have a method named $name", null))

      val interfaceMethodSignature = interfaceMethod.signature()
      val valueMethodSignature = valueMethod.signature()

      val conversion = when (val result = interfaceMethodSignature.assignableFrom(valueMethodSignature)) {
        // TODO: Location
        is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error.wrap("Incompatible method $name", null))
        is PossibleTypeError.Success -> result.value
      }

      Pair(name, ConverterMethod(interfaceMethodSignature, valueMethod, conversion))
    }.toMap()

    return PossibleTypeError.Success { PhotonInterfaceInstance(this, it, methodTable) }
  }

  val virtualMethods by lazy {
    properties.value.associate { methodForVirtualFunction(it) }
  }

  override val methods: Map<String, Method> by lazy {
     virtualMethods + functions.value.associate { methodForConcreteFunction(it) }
  }

  private fun methodForVirtualFunction(property: Definitions.Property): Pair<String, VirtualMethod> {
    val method = VirtualMethod(property)

    return Pair(property.name, method)
  }

  private fun methodForConcreteFunction(function: Definitions.Function): Pair<String, Method> {
    // TODO: Use MethodType based on the function itself
    val method = object: Method(MethodType.Default) {
      private val callMethod by lazy {
        function.function.type().getMethod("call", null)
          ?: throw EvalError("Function is not callable", null)
      }

      override fun signature() = callMethod.signature().withoutSelfArgument()

      override fun call(target: Any, vararg args: Any): Any {
        val shouldPassSelf = callMethod.signature().hasSelfArgument()

        return if (shouldPassSelf) {
          callMethod.call(function.function, target, *args)
        } else {
          callMethod.call(function.function, *args)
        }
      }
    }

    return Pair(function.name, method)
  }

  // TODO: Support other method types?
  class VirtualMethod(
    private val property: Definitions.Property
  ): Method(MethodType.Default) {
    val argumentTypes by lazy {
      val type = property.type

      if (type is PhotonFunctionalInterface) {
        type.parameters.map { it.second }
      } else {
        // Property getter
        emptyList()
      }
    }

    override fun signature(): Signature {
      val type = property.type

      return if (type is PhotonFunctionalInterface) {
        Signature.Concrete(type.parameters, type.returnType)
      } else {
        // Property getter
        Signature.Concrete(emptyList(), type)
      }
    }

    override fun call(target: Any, vararg args: Any): Any {
      return InteropLibrary.getUncached().invokeMember(target, property.name, *args)
    }
  }
}

class PhotonFunctionalInterface(
  val parameters: List<Pair<String, Type>>,
  val returnType: Type
): Interface() {
  // TODO: Somehow unify with the other class for interfaces?
  override fun conversionFrom(other: Type): PossibleTypeError<ValueConverter> {
    val originalMethod = other.getMethod("call", parameters.map { it.second })
      ?: // TODO: Location
      return PossibleTypeError.Error(TypeError("Type $other does not have a method named call", null))

    val interfaceMethodSignature = Signature.Concrete(parameters, returnType)
    val valueMethodSignature = originalMethod.signature()

    val conversion = when (val result = interfaceMethodSignature.assignableFrom(valueMethodSignature)) {
      is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error)
      is PossibleTypeError.Success -> result.value
    }

    val converterMethod = ConverterMethod(interfaceMethodSignature, originalMethod, conversion)

    val methodTable = mapOf(
      Pair("call", converterMethod)
    )

    return PossibleTypeError.Success { PhotonInterfaceInstance(this, it, methodTable) }
  }

  // TODO: Somehow unify with the other class for interfaces?
  override val methods: Map<String, Method> = mapOf(
    Pair("call", object: Method(MethodType.Default) {
      override fun signature(): Signature = Signature.Concrete(parameters, returnType)
      override fun call(target: Any, vararg args: Any): Any {
        val self = target as PhotonInterfaceInstance

        val method = self.methodTable["call"]!!

        return method.call(self.value, *args)
      }
    })
  )
}