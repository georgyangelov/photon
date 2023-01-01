package photon.compiler.libraries

import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.*
import photon.compiler.core.EvalMode
import photon.compiler.core.Type

@GenerateLibrary
abstract class ValueLibrary: Library() {
  open fun type(receiver: Any): Type {
    throw UnsupportedMessageException.create()
  }
}
