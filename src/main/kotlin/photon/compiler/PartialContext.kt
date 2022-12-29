package photon.compiler

import photon.compiler.core.EvalMode
import photon.compiler.core.Type

class PartialContext(
  val module: PhotonModule,
  val evalMode: EvalMode,
  val argumentTypes: List<Type>,
) {
//  val localTypes = mutableMapOf<Int, Type>()
}