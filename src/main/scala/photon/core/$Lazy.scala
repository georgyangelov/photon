package photon.core

import photon.base._
import photon.frontend.ASTValue
import photon.lib._

case class $Lazy(value: Lazy[Value], location: Option[Location]) extends Value {
  override def isOperation = value.resolve.isOperation
  override def evalMayHaveSideEffects = value.resolve.evalMayHaveSideEffects
  override def unboundNames = value.resolve.unboundNames
  override def typ(scope: Scope) = value.resolve.typ(scope)
  override def evaluate(env: Environment) = value.resolve.evaluate(env)
  override def toAST(names: Map[VarName, String]): ASTValue = value.resolve.toAST(names)
  override def partialValue(env: Environment, followReferences: Boolean) = value.resolve.partialValue(env, followReferences)

  override def asType: Type = $LazyType(Lazy.of(() => {
    value.resolve match {
      case typ: Type => typ
      case resolved => throw EvalError(s"Expected value $resolved to be a type", location)
    }
  }))
}
