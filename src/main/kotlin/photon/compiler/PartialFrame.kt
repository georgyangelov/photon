package photon.compiler

import com.oracle.truffle.api.frame.Frame
import photon.compiler.core.Type

class PartialFrame(
  val frame: Frame,
  val parent: PartialFrame?,
  val arguments: Map<Int, Type>,
) {
  val localTypes = mutableMapOf<Int, Type>()
}