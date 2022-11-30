package photon.core.operations

import photon.base._
import photon.frontend.ASTValue

case class $Block(values: Seq[Value], location: Option[Location]) extends Value {
  override def isOperation = true
  override def evalMayHaveSideEffects = values.exists(_.evalMayHaveSideEffects)
  override def unboundNames = values.flatMap(_.unboundNames).toSet

  override def typ(scope: Scope) = values.last.typ(scope)
  override def evaluate(env: Environment) = {
    val lastIndex = values.length - 1
    val eValueBuilder = Seq.newBuilder[Value]
    val closures = Seq.newBuilder[Closure]

    values.zipWithIndex.foreach { case (value, index) =>
      val evalue = value.evaluate(env)

      if (evalue.value.evalMayHaveSideEffects || index == lastIndex) {
        eValueBuilder.addOne(evalue.value)
        closures.addAll(evalue.closures)
      }
    }

    val eValues = eValueBuilder.result

    val result = if (eValues.length == 1) {
      eValues.last
    } else {
      $Block(eValues, location)
    }

    EvalResult(result, closures.result)
  }

  override def toAST(names: Map[VarName, String]): ASTValue =
    ASTValue.Block(values.map(_.toAST(names)), location)
}
