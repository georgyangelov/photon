package photon

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class PhotonRootNode(language: PhotonLanguage, value: Value) extends RootNode(language) {
  override def execute(frame: VirtualFrame): AnyRef =
    value.executeGeneric(frame)
}