package photon.compiler.libraries

import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.*
import photon.compiler.core.EvalMode

@GenerateLibrary
abstract class PhotonLibrary: Library() {
  @Throws(UnknownIdentifierException::class)
  open fun invokeMember(receiver: Any, evalMode: EvalMode, member: String, vararg arguments: Any): Any {
    throw UnsupportedMessageException.create()
  }
}
