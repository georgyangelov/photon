package photon.compiler.values

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import photon.compiler.core.*
import photon.compiler.types.classes.PhotonConcreteInterfaceType

class PhotonInterfaceInstance(
  val type: PhotonConcreteInterfaceType,
  val value: Any,
): PhotonObject(type)