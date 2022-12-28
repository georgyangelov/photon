package photon.compiler.libraries

import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.Library

@GenerateLibrary
//@GenerateLibrary.DefaultExport(
//  IntegerMethods::class
//)
abstract class PhotonLibrary : Library() {
  open fun invokeMember(receiver: Any, member: String, vararg arguments: Any): Any {
    return false
  }
}