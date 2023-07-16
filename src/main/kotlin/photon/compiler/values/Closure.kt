package photon.compiler.values

import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*
import photon.compiler.libraries.ValueLibrary

@ExportLibrary(ValueLibrary::class)
@ExportLibrary(InteropLibrary::class)
class Closure(
  val _type: Type,
  val capturedFrame: MaterializedFrame
): TruffleObject {
  @ExportMessage
  fun type() = _type

  @ExportMessage
  fun isExecutable() = true

  @ExportMessage
  fun execute(vararg arguments: Any): Any {
    // TODO: Specify correct EvalMode
    // TODO: Support template functions by specifying the argTypes
    return _type.getMethod("call", null)!!.call(EvalMode.CompileTimeOnly, this, *arguments)
  }
}