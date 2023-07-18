package photon.compiler.core

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

abstract class Method(val type: MethodType) {
  abstract fun signature(): Signature

  fun methodType(): MethodType = type

  abstract fun call(target: Any, vararg args: Any): Any
}
