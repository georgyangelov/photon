package photon.compiler.core

import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.libraries.MethodLibrary

sealed class MethodCallThrowable: Throwable()
object CannotCallRunTimeMethodInCompileTimeMethod: MethodCallThrowable()
object CannotCallCompileTimeMethodInRunTimeMethod: MethodCallThrowable()
object DelayCall: MethodCallThrowable()

enum class MethodType {
  Default,
  Partial,
  CompileTimeOnly,
  PreferRunTime,
  RunTimeOnly
}

@ExportLibrary(MethodLibrary::class)
abstract class Method(val type: MethodType) {
  @ExportMessage
  abstract fun signature(): Signature

  @ExportMessage
  fun methodType(): MethodType = type

  @ExportMessage
  abstract fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any
}
