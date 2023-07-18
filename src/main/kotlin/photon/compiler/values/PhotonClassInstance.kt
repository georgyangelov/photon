package photon.compiler.values

import photon.compiler.core.PhotonObject

abstract class PhotonClassInstance(type: PhotonClass): PhotonObject(type)

interface PhotonStaticObjectFactory {
  fun create(type: PhotonClass): Any
}