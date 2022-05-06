package photon.support

import photon.base.EValue

object TestHelpers {
  def expectCompileTime(macros: String, code: String, expected: String) = ???
  def expectCompileTime(code: String, expected: String) = ???
  def expectPhases(code: String, compile: String, run: String) = ???
  def toEValue(code: String): EValue = ???
}
