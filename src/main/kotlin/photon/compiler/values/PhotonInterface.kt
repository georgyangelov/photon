package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary

@ExportLibrary(ValueLibrary::class)
class PhotonInterface(
  val builder: ClassBuilder
): Interface() {
  @ExportMessage
  fun type() = PhotonInterfaceType(this, builder)

  override fun assignableFrom(other: Type): PossibleTypeError<NodeWrapper> {
    TODO("Not yet implemented")
  }

  // TODO: Support methods defined directly on the interface
  override val methods: Map<String, Method> by lazy {
    builder.properties.associate { methodForVirtualFunction(it) }
  }

  private fun methodForVirtualFunction(property: ClassBuilder.Property): Pair<String, Method> {
    // TODO: Support other method types?
    val method = object: Method(MethodType.Default) {
      override fun signature(): Signature {
        TODO("Not yet implemented")
      }

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        TODO("Not yet implemented")
      }
    }

    return Pair(property.name, method)
  }
}

class PhotonInterfaceType(
  val iface: PhotonInterface,
  val builder: ClassBuilder
): Type() {
  override val methods: Map<String, Method> by lazy {
    TODO()
  }
}

class PhotonFunctionalInterface(

): Interface() {
  override fun assignableFrom(other: Type): PossibleTypeError<NodeWrapper> {
    TODO("Not yet implemented")
  }

  override val methods: Map<String, Method>
    get() = TODO("Not yet implemented")
}