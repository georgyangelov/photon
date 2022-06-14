package photon.base

import photon.Core
import photon.core._
import photon.frontend._

class Interpreter {
  val core = new Core

  def toEValue(uvalue: UValue, scope: Scope): EValue = ???
  def toEValue(upattern: UPattern, scope: Scope): ($Pattern.Value, Scope) = ???
}
