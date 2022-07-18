package photon.base

import photon.core.operations.$Let

case class PartialValue(value: Value, variables: Seq[(VarName, Value)]) {
  def withOuterVariable(name: VarName, outerValue: Value) = {
    PartialValue(value, (name -> outerValue) +: variables)
  }

  def evaluate(env: Environment) = {
    val envWithVariables = Environment(
      env.scope.newChild(variables),
      env.evalMode
    )

    PartialValue(
      value.evaluate(envWithVariables),
      variables
    )
  }

  def wrapInLets: Value =
    variables.foldRight(value) { case (varName, varValue) -> result =>
      if (result.unboundNames.contains(varName)) {
        $Let(varName, varValue, result, varValue.location)
      } else {
        result
      }
    }
}
