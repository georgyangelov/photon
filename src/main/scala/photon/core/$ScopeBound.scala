package photon.core

import photon.base._

case class $ScopeBound(value: Value, scope: Scope) extends Value {
  override def isOperation = value.isOperation
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects
  override def location = value.location
  override def unboundNames = value.unboundNames
  override def typ(_scope: Scope) = value.typ(scope)
  override def evaluate(env: Environment) = value.evaluate(Environment(scope, env.evalMode))
//    $ScopeBound(
//      value.evaluate(Environment(scope, env.evalMode)),
//      scope
//    )

  override def toAST(names: Map[VarName, String]) = value.toAST(names)
  override def partialValue(env: Environment, followReferences: Boolean) =
    value.partialValue(Environment(scope, env.evalMode), followReferences)
}
