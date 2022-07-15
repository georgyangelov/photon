package photon.interpreter

import photon.base._
import photon.frontend.{ASTToValue, ASTValue, StaticScope}

class Interpreter {
  val rootScope = Scope.newRoot(Seq.empty)

  def evaluate(ast: ASTValue, evalMode: EvalMode): Value = {
    val value = ASTToValue.transform(ast, StaticScope.fromRootScope(rootScope))

    evaluate(value, evalMode)
  }

  def evaluate(value: Value, evalMode: EvalMode): Value = {
    value.evaluate(rootScope, evalMode)
  }
}
