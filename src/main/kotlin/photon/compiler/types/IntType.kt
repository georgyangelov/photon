package photon.compiler.types

import com.oracle.truffle.api.library.*
import photon.compiler.core.*
import photon.compiler.libraries.PhotonValueLibrary

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@ExportLibrary(PhotonValueLibrary::class, receiverType = Integer::class)
object IntTypeDefaultLibraryExports {
  @JvmStatic
  @ExportMessage
  fun isPhotonValue(receiver: Integer) = true

  @JvmStatic
  @ExportMessage
  fun type(receiver: Integer) = IntType
}

object IntType: Type() {
  override val methods = mapOf(
    Pair("+", PlusMethod)
  )

  object PlusMethod: Method(MethodType.Default) {
    override fun signature(): Signature = Signature.Concrete(
      listOf(Pair("other", IntType)),
      IntType
    )

    override fun call(target: Any, vararg args: Any): Any {
      return target as Int + args[0] as Int
    }
  }
}