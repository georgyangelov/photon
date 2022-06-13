package photon.base

class VarName(val originalName: String)
class EVarName(originalName: String) extends VarName(originalName)

case class Variable(name: EVarName, value: EValue)
