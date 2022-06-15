package photon.base

object Scope {
  def newRoot(variables: Seq[Variable]): Scope = {
    Scope(
      None,
      variables.map { variable => variable.name -> variable }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[VarName, Variable]) {
//  def newChild(variables: Seq[Variable]): Scope = {
//    Scope(
//      Some(this),
//      variables.map { variable => variable.name -> variable }.toMap
//    )
//  }

  def newChild(variables: Seq[(VarName, Variable)]) = Scope(Some(this), variables.toMap)

  override def toString: String = {
    val values = variables.map { case name -> variable => name.originalName -> variable.value.toString }

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: VarName): Option[Variable] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}