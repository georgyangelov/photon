package photon.compiler.values

import photon.compiler.core.*
import photon.compiler.types.classes.PhotonConcreteInterfaceType

class PhotonInterfaceInstance(
  type: PhotonConcreteInterfaceType,
  val value: Any,
): PhotonObject(type)