package photon.compiler.libraries

import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.GenerateLibrary.Abstract
import com.oracle.truffle.api.library.Library
import photon.compiler.core.Method
import photon.compiler.core.Type

@GenerateLibrary
abstract class TypeLibrary: Library() {
  @Abstract
  open fun getMethod(receiver: Any, name: String, argTypes: List<Type>?): Method? {
    throw UnsupportedMessageException.create()
  }
}
