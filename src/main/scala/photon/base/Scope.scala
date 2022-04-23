package photon.base

object Scope {
  def newRoot(variables: Seq[Variable]): Scope = {
    Scope(
      None,
      variables.map { variable => variable.name -> variable }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[VariableName, Variable]) {
  def newChild(variables: Seq[Variable]): Scope = {
    Scope(
      Some(this),
      variables.map { variable => variable.name -> variable }.toMap
    )
  }

  override def toString: String = {
    val values = variables.map { case name -> variable => name.originalName -> variable.value.toString }

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: VariableName): Option[Variable] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}