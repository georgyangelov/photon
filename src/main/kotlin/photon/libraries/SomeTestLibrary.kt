package photon.libraries

import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.Library

@GenerateLibrary
abstract class SomeTestLibrary : Library() {
  open fun invokeMember(receiver: Any, member: Any, vararg arguments: Any): Any {
    return false
  }
}