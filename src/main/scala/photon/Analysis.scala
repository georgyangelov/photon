package photon

case class AnalyzedValue(analysis: Analysis, value: Value) {
  def replaceWith(newValue: Value): AnalyzedValue = {
    analysis.valueReplaced(value, newValue)

    AnalyzedValue(analysis, newValue)
  }
}

class Analysis {
  def analyze(value: Value): AnalyzedValue = {
    // TODO: Iterate and analyze

    AnalyzedValue(this, value)
  }

  def forget(value: Value): Unit = {

  }

  def valueReplaced(oldValue: Value, newValue: Value): Unit = {

  }

  def isStatic(value: Value): Boolean = ???
}
