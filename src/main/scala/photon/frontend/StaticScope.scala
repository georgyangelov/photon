package photon.frontend

import photon.base.{Scope, VarName}

sealed trait StaticScope {
  def root: StaticScope.Root
  def find(name: String): Option[VarName]

  def newChild(variables: Map[String, VarName]): StaticScope = {
    StaticScope.Child(root, this, variables)
  }
}

object StaticScope {
  case class Root(variables: Map[String, VarName]) extends StaticScope {
    override def root: Root = this
    override def find(name: String): Option[VarName] = variables.get(name)

    override def toString: String = {
      val names = variables.view
        .mapValues(_.originalName)
        .mkString(", ")

      s"{ $names }"
    }
  }

  case class Child(
    root: StaticScope.Root,
    parent: StaticScope,
    variables: Map[String, VarName]
  ) extends StaticScope {
    override def find(name: String): Option[VarName] = variables.get(name) orElse { parent.find(name) }

    override def toString: String = {
      val names = variables.view
        .mapValues(_.originalName)
        .mkString(", ")

      s"{ $names } -> ${parent.toString}"
    }
  }

  def newRoot(variables: Seq[VarName]): StaticScope.Root =
    StaticScope.Root(
      variables.map { variable => (variable.originalName, variable) }.toMap
    )

  def fromRootScope(scope: Scope): StaticScope.Root = {
    val variables = scope.variables
      .map { case name -> _ => name.originalName -> name }
      .toMap

    StaticScope.Root(variables)
  }
}
