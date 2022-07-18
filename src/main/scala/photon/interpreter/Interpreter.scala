package photon.interpreter

import photon.base._
import photon.core.objects._
import photon.frontend._

class Interpreter {
  val rootScope = Scope.newRoot(Seq(
    new VarName("Int") -> $Int
  ))

  def evaluate(ast: ASTValue, evalMode: EvalMode): Value = {
    val value = ASTToValue.transform(ast, StaticScope.fromRootScope(rootScope))

    evaluate(value, evalMode)
  }

  def evaluate(value: Value, evalMode: EvalMode): Value = {
    val env = Environment(rootScope, evalMode)

    value.evaluate(env)
  }
}
