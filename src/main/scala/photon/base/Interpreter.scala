package photon.base

import photon.Core
import photon.frontend._

class Interpreter {
  val core = new Core

  def toEValue(uvalue: UValue, scope: Scope): EValue = ???
  def toEPattern(upattern: UPattern, scope: Scope): Pattern = ???
}
