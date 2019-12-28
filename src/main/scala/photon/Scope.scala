package photon

abstract sealed class Scope {
  abstract def find(name: String): Option[Value]
}

case object RootScope extends Scope {
  override def toString: String = "RootScope"
  override def find(name: String): Option[Value] = None
}

case class LambdaScope(parent: Scope, lambda: Lambda, values: Map[String, Value]) extends Scope {
  override def toString: String = s"$values -> ${parent.toString}"
  override def find(name: String): Option[Value] = values.get(name) orElse { parent.find(name) }
}
