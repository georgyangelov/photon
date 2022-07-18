package photon.core.operations

import photon.base._
import photon.frontend.ASTValue

case class $Reference(name: VarName, location: Option[Location]) extends Value {
  override def isOperation = true
  override def unboundNames = Set(name)

  override def evaluate(env: Environment) = env.evalMode match {
    case EvalMode.CompileTimeOnly |
         EvalMode.RunTime => valueInScope(env.scope).evaluate(env)

    // If it's in a partial mode => it's not required (yet)
    case EvalMode.Partial |
         EvalMode.PartialPreferRunTime |
         EvalMode.PartialRunTimeOnly => this
  }

  override def typ(scope: Scope) = valueInScope(scope).typ(scope)

  private def valueInScope(scope: Scope) =
    scope.find(name)
      .getOrElse {
        throw EvalError(s"Cannot find name $name in scope $scope", location)
      }

  override def toAST(names: Map[VarName, String]): ASTValue =
    ASTValue.NameReference(names(name), location)

  override def partialValue(env: Environment, followReferences: Boolean) =
    if (followReferences) {
      valueInScope(env.scope).partialValue(env, followReferences)
    } else {
      PartialValue(this, Seq.empty)
    }
}
