package photon.compiler.values

import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*

@ExportLibrary(InteropLibrary::class)
class Closure(
  val type: Type,
  val capturedFrame: MaterializedFrame
): PhotonObject(type) {
  @ExportMessage
  fun isExecutable() = true

  @ExportMessage
  fun execute(vararg arguments: Any): Any {
    // TODO: Specify correct EvalMode
    // TODO: Support template functions by specifying the argTypes
    return type.getMethod("call", null)!!.call(this, *arguments)
  }
}