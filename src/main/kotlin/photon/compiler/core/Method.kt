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

  // Calling through this is probably very slow, but I'm doing it anyway for now
//  val javaMethod = this.javaClass.methods.find { it.name == "call" }!!
//
//  abstract class Default: Method() {
//    final override fun callMethod(target: Any, args: Array<Any?>, evalMode: EvalMode): Any {
//      when (evalMode) {
//        EvalMode.CompileTimeOnly, EvalMode.RunTime, EvalMode.Partial -> return javaMethod.invoke(this, target, *args)
//        EvalMode.PartialPreferRunTime, EvalMode.PartialRunTimeOnly -> throw DelayCall
//      }
//    }
//  }
}
