package photon.compiler.values

import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.*

class Closure(
  type: Type,
  val capturedFrame: MaterializedFrame
): PhotonObject(type)