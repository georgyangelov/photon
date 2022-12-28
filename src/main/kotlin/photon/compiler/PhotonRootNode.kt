package photon.compiler

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.Value

class PhotonRootNode(language: TruffleLanguage<*>, private val value: Value) : RootNode(language) {
  override fun execute(frame: VirtualFrame): Any {
    return value.executeGeneric(frame)
  }
}