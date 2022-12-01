package photon.core.operations

import photon.base._
import photon.core.$Type
import photon.frontend.ASTValue

case class $Unknown(valueType: Type, location: Option[Location]) extends Value {
  override def isOperation: Boolean = true

  // TODO: Is this correct?
  override def evalMayHaveSideEffects: Boolean = false

  override def unboundNames: Set[VarName] = Set.empty

  override def typ(scope: Scope): Type = valueType

  override def evaluate(env: Environment) =
    throw EvalError("Tried to eval an unknown value, this shouldn't happen", location)

  override def toAST(names: Map[VarName, String]): ASTValue =
    throw EvalError("Tried to convert an unknown value to AST, this shouldn't happen", location)
}
