package photon

class PhotonError(
  message: String,
  val location: Option[Location] = None
) extends Exception(message) {
}
