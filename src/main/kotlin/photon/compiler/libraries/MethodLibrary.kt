package photon.compiler.libraries

import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.GenerateLibrary.Abstract
import com.oracle.truffle.api.library.Library
import photon.compiler.core.*

@GenerateLibrary
abstract class MethodLibrary: Library() {
  @Abstract
  open fun methodType(receiver: Any): MethodType {
    throw UnsupportedMessageException.create()
  }

  @Abstract
  open fun signature(receiver: Any): Signature {
    throw UnsupportedMessageException.create()
  }

  // TODO: Different specializations based on EvalMode, or different methods?
  @Abstract
  open fun call(receiver: Any, evalMode: EvalMode, target: Any, vararg args: Any): Any {
    throw UnsupportedMessageException.create()
  }
}
