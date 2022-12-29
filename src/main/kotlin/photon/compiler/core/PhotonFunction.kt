package photon.compiler.core

import com.oracle.truffle.api.frame.FrameDescriptor
import photon.compiler.*

class PhotonFunction(
  private val body: PhotonFunctionRootNode
) {
  val requiredCaptures: Array<NameCapture>
    get() = body.captures

  val argumentCaptures: Array<ArgumentCapture>
    get() = body.argumentCaptures

  val frameDescriptor: FrameDescriptor
    get() = body.frameDescriptor

  fun executePartial(module: PhotonModule) {
    body.executePartial(module)
  }

  fun call(captures: Array<Any>, vararg arguments: Any): Any {
    return body.callTarget.call(captures, *arguments)
  }
}