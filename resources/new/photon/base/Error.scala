package photon.base

class PhotonError(
  message: String,
  val location: Option[Location] = None
) extends Exception(s"$message${location.map { l => s" @ ${l.fileName}:${l.startLine}:${l.endCol}" }.getOrElse("")}") {
}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}
