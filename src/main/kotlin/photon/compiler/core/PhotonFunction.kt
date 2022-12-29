package photon.compiler.core

import photon.compiler.PhotonFunctionRootNode

class PhotonFunction(
  val body: PhotonFunctionRootNode
) {
  fun call(vararg arguments: Any): Any {
    return body.callTarget.call(*arguments)
  }
}