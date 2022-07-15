package photon.core

import photon.base._
import photon.frontend.ASTValue
import photon.lib._

case class $Lazy(value: Lazy[Value], location: Option[Location]) extends Value {
  override def typ(scope: Scope) = value.resolve.typ(scope)
  override def evaluate(scope: Scope, evalMode: EvalMode) = value.resolve.evaluate(scope, evalMode)
  override def toAST(names: Map[VarName, String]): ASTValue = value.resolve.toAST(names)
}
