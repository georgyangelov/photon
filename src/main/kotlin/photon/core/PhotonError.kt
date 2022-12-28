package photon.core

open class PhotonError(
  message: String,
  val location: Location? = null
) : Exception(
  "$message${
    if (location != null) {
      " @ ${location.fileName}:${location.startLine}:${location.endCol}"
    } else ""
  }"
) {}

class EvalError(message: String, location: Location?) : PhotonError(message, location)

class TypeError(
  message: String,
  location: Location?
) : PhotonError(message, location)
