package photon.base

class Interpreter {
  def core: Any = ???
  def toEValue(uvalue: Any, scope: Scope): Any = ???
}
