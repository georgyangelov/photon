package photon.compiler.types

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.PhotonValueLibrary

// TODO: Convert to TruffleString
@ExportLibrary(PhotonValueLibrary::class, receiverType = String::class)
object StringTypeDefaultLibraryExports {
  @JvmStatic
  @ExportMessage
  fun isPhotonValue(receiver: String) = true

  @JvmStatic
  @ExportMessage
  fun type(receiver: String) = StringType
}

object StringType: Type() {
  override val methods = mapOf<String, Method>()
}