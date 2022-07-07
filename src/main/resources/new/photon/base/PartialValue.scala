package photon.base

case class PartialValue(value: EValue, variables: Seq[Variable]) {
  def replaceWith(newValue: EValue) = PartialValue(newValue, variables)
  def addOuterVariables(additionalVars: Seq[Variable]) = PartialValue(value, additionalVars ++ variables)
  def addInnerVariables(additionalVars: Seq[Variable]) = PartialValue(value, variables ++ additionalVars)
  def withOuterVariable(variable: Variable) = PartialValue(value, variables :+ variable)

  def wrapBack: EValue = ???
//    variables.foldRight(value) { case (Variable(name, varValue), innerValue) =>
//      if (innerValue.unboundNames.contains(name)) {
//        LetValue(name, varValue, innerValue, varValue.location)
//      } else {
//        innerValue
//      }
//    }
}
