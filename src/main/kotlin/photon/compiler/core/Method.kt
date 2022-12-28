package photon.compiler.core

sealed class MethodCallThrowable: Throwable()
object CannotCallRunTimeMethodInCompileTimeMethod: MethodCallThrowable()
object CannotCallCompileTimeMethodInRunTimeMethod: MethodCallThrowable()
object DelayCall: MethodCallThrowable()

sealed class Method {
  abstract val signature: Signature
//  protected abstract fun call(vararg args: Any): Any

  abstract fun callMethod(args: Array<out Any>, evalMode: EvalMode): Any

  // Calling through this is probably very slow, but I'm doing it anyway for now
  val javaMethod = this.javaClass.methods.find { it.name == "call" }!!

  abstract class Default: Method() {
    final override fun callMethod(args: Array<out Any>, evalMode: EvalMode): Any {
      when (evalMode) {
        EvalMode.CompileTimeOnly, EvalMode.RunTime, EvalMode.Partial -> return javaMethod.invoke(this, *args)
        EvalMode.PartialPreferRunTime, EvalMode.PartialRunTimeOnly -> throw DelayCall
      }
    }
  }
}
