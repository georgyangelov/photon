package photon.compiler.types

import com.oracle.truffle.api.interop.InteropLibrary
import photon.compiler.core.*
import kotlin.Any

object InteropNative {
  fun loadClass(name: String): Class<*> {
    return Class.forName(name)
  }
}

object InteropMetaType: Type() {
  var native: Any? = null

  override val methods = mapOf(
    "loadClass" to LoadClassMethod,
    "setNativeValue" to SetNativeValueMethod
  )

  object SetNativeValueMethod: Method(MethodType.PreferRunTime) {
    override fun signature() = Signature.Any(AnyType)

    override fun call(target: Any, vararg args: Any): Any {
      native = args[0]

      return args[0]
    }
  }

  object LoadClassMethod: Method(MethodType.PreferRunTime) {
    override fun signature() = Signature.Concrete(
      listOf("className" to StringType),
      AnyType
    )

    override fun call(target: Any, vararg args: Any): Any {
      // TODO: Optimize
      return InteropLibrary.getUncached(native).invokeMember(native, "loadClass", *args)
    }
  }
}
