package photon.core.operations

import photon.base._
import photon.frontend.ASTValue

case class $Block(values: Seq[Value], location: Option[Location]) extends Value {
  override def isOperation = true
  override def evalMayHaveSideEffects = values.exists(_.evalMayHaveSideEffects)
  override def unboundNames = values.flatMap(_.unboundNames).toSet

  override def typ(scope: Scope) = values.last.typ(scope)
  override def evaluate(env: Environment) = {
    // TODO: Remove values that don't have side effects and are not last
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
