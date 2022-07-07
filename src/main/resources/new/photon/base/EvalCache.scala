package photon.base

import scala.collection.mutable

class EvalCache {
  private val cache = new mutable.HashMap[(EValue, EvalMode), EValue]

  def evaluate(value: EValue, evalMode: EvalMode): EValue =
    cache.getOrElseUpdate((value, evalMode), value.evaluate(evalMode))
}
