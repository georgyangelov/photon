package photon

class PhotonError(
  message: String,
  val location: Option[Location] = None
) extends Exception(s"$message @ ${location.map { l => s"${l.fileName}:${l.startLine}:${l.endCol}" }}") {
}
