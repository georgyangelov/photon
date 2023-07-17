package photon.compiler.libraries

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.GenerateLibrary.Abstract
import com.oracle.truffle.api.library.Library
import photon.compiler.core.*

@GenerateLibrary
abstract class PhotonValueLibrary: Library() {
  open fun isPhotonValue(receiver: Any) = false

  @Abstract(ifExported = ["isPhotonValue"])
  open fun getMethod(receiver: Any, name: String, argTypes: List<Type>?): Method? {
    if (!InteropLibrary.getFactory().uncached.isMemberInvocable(receiver, name)) {
      return null
    }

    // TODO: Refactor by removing the Method class
    return object: Method(MethodType.PreferRunTime) {
      override fun signature(): Signature {
        TODO("Not yet implemented")
      }

      override fun call(evalMode: EvalMode, target: Any, vararg args: Any): Any {
        // TODO: Make this use the cached version
        return InteropLibrary.getFactory().uncached.invokeMember(target, name, *args)
      }
    }
  }
}