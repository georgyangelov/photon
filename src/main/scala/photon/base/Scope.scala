package photon.base

object Scope {
  def newRoot(variables: Seq[(VarName, EValue)]) =
    Scope(parent = None, variables)
}

case class Scope(parent: Option[Scope], var variables: Seq[(VarName, EValue)]) {
  def newChild(variables: Seq[(VarName, EValue)]) =
    Scope(parent = Some(this), variables)

  override def toString: String = {
    val values = variables.map { case name -> value => name.originalName -> value.toString }

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: VarName): Option[EValue] = {
    variables
      .find { case varName -> _ => varName == name }
      .map(_._2)
      .orElse { parent.flatMap(_.find(name)) }
  }

  def dangerouslySetValue(name: VarName, value: EValue): Unit = {
    variables = variables ++ Seq(name -> value)
  }
}