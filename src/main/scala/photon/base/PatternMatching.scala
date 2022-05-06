package photon.base

import photon.Core
import photon.frontend.UPattern
import photon.lib.ScalaExtensions._

sealed trait Pattern {
  def bindings: Seq[VariableName]
  def toUPattern(core: Core): UPattern
  def unboundNames: Set[VariableName]

  def isSubsetOf(other: Pattern): Boolean = ???

  def matchValue(value: EValue): Option[PatternMatch] = this match {
    case Pattern.SpecificValue(selfValue) =>
      if (EValue.equals(selfValue, value)) {
        Some(PatternMatch(Seq.empty))
      } else {
        None
      }

    case Pattern.Val(name) =>
      // TODO: Add name -> other to bindings
      ???

    case Pattern.Call(target, name, args) =>
      // TODO: Call method `target.<name>`, then call `matchValue` on each of the
      //       results.
      ???
  }
}
object Pattern {
  case class SpecificValue(value: EValue) extends Pattern {
    override def bindings = Seq.empty
    override def toUPattern(core: Core) = UPattern.SpecificValue(value.toUValue(core))
    override def unboundNames = value.unboundNames
  }

  case class Val(name: VariableName) extends Pattern {
    override def bindings = Seq(name)
    override def toUPattern(core: Core) = ??? // UPattern.Val(name)
    override def unboundNames = Set.empty
  }

  case class Call(target: EValue, name: String, args: Arguments[Pattern]) extends Pattern {
    override def bindings = (args.positional ++ args.named.values).flatMap(_.bindings)
    override def toUPattern(core: Core) = ???
    override def unboundNames = (args.positional ++ args.named.values).map(_.unboundNames).unionSets
  }
}

case class PatternMatch(bindings: Seq[(VariableName, EValue)])
