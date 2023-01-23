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
  location: Location?,
  reason: TypeError? = null
) : PhotonError("$message${if (reason != null) " -> ${reason.message}" else ""}", location) {
  fun wrap(message: String, location: Location?) = TypeError(message, location, this)
}
