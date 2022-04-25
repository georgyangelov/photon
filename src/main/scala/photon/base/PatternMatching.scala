package photon.base

import photon.Core
import photon.frontend.UPattern

sealed trait Pattern {
  def bindings: Seq[String]
  def toUPattern(core: Core): UPattern
  def unboundNames: Set[VariableName]
}
object Pattern {
  case class SpecificValue(value: EValue) extends Pattern {
    override def bindings = Seq.empty
    override def toUPattern(core: Core) = UPattern.SpecificValue(value.toUValue(core))
    override def unboundNames = value.unboundNames
  }

  case class Val(name: String) extends Pattern {
    override def bindings = Seq(name)
    override def toUPattern(core: Core) = UPattern.Val(name)
    override def unboundNames = Set.empty
  }
}

case class PatternMatch(bindings: Seq[(String, EValue)])

object PatternMatching {
  def matchValue(pattern: Pattern, value: EValue): Option[PatternMatch] = ???
}
