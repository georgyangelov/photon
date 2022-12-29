package photon.compiler.core

import photon.compiler.PhotonRootNode

class PhotonFunction(
  val body: PhotonRootNode
) {
  fun call(vararg arguments: Any): Any {
    return body.callTarget.call(*arguments)
  }
}