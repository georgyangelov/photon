package photon.compiler.values

import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary

@ExportLibrary(ValueLibrary::class)
class Closure(
  val function: PhotonFunction,
  val capturedFrame: MaterializedFrame
) {
  @ExportMessage
  fun type() = function.type
}