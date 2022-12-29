package photon.compiler

import photon.compiler.core.EvalMode

class PartialContext(
  val module: PhotonModule,
  val evalMode: EvalMode
)