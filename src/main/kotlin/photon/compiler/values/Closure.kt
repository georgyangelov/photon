package photon.compiler.values

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary

@ExportLibrary(ValueLibrary::class)
class Closure(
  val function: PhotonFunction,
  val captures: Array<Any>
) {
  @ExportMessage
  fun type() = function.type
}