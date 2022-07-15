package photon.core.operations

import photon.base._
import photon.frontend.ASTValue

case class $Block(values: Seq[Value], location: Option[Location]) extends Value {
  override def typ(scope: Scope) = values.last.typ(scope)
  override def evaluate(env: Environment) = {
    val eValues = values.map(_.evaluate(env))

    if (eValues.length == 1) {
      eValues.last
    } else {
      $Block(eValues, location)
    }
  }

  override def toAST(names: Map[VarName, String]): ASTValue =
    ASTValue.Block(values.map(_.toAST(names)), location)
}
