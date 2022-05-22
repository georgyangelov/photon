package photon.base

import photon.Core
import photon.core.operations.$Call
import photon.core.{$Bool, $Type}
import photon.frontend.UPattern
import photon.lib.ScalaExtensions._

sealed trait Pattern {
  def bindings: Seq[VariableName]
  def toUPattern(core: Core): UPattern
  def unboundNames: Set[VariableName]

  def isSubsetOf(other: Pattern): Boolean = ???

  def matchValue(value: EValue, location: Option[Location]): Option[PatternMatchResult] = this match {
    case Pattern.SpecificValue(selfValue) =>
      if (EValue.equals(selfValue, value)) {
        Some(PatternMatchResult(Seq.empty))
      } else {
        None
      }

    case Pattern.Val(name) =>
      Some(PatternMatchResult(Seq(name -> value)))

    case Pattern.Call(target, name, args) =>
      // TODO: Call method `target.<name>`, then call `matchValue` on each of the
      //       results.
      val matchCall = $Call.Value(name, Arguments.empty(target), location)
      val matchResult = matchCall.evaluated(EvalMode.CompileTimeOnly) match {
        case 
      }


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

case class PatternMatchResult(bindings: Seq[(VariableName, EValue)]) {

}

//object $Pattern extends Type {
//  override def typ = $Type
//  override val methods = Map.empty
//  override def toUValue(core: Core) = ???
//
//  case class Value(pattern: Pattern, location: Option[Location]) extends EValue {
//    override def unboundNames = pattern.unboundNames
//    override def toUValue(core: Core) = ???
//    override def evalMayHaveSideEffects = true
//    override def typ = $Pattern
//    override def realType =
//
//    /**
//     * An internal-only method, please use `evaluated` instead.
//     *
//     * This method does the actual evaluation and DOES NOT perform caching
//     * as `evaluated` does. Additionally, it DOES NOT set the EvalMode in
//     * the context.
//     */
//    override protected[base] def evaluate(mode: EvalMode): EValue = ???
//  }
//}
//
//// TODO: This can become an instance of a class
//case class PatternMatchResult(bindings: Seq[(VariableName, EValue)], location: Option[Location]) extends EValue {
//  override def unboundNames = bindings.map(_._2).flatMap(_.unboundNames).toSet
//  override def toUValue(core: Core) = ???
//  override def evalMayHaveSideEffects = bindings.map(_._2).exists(_.evalMayHaveSideEffects)
//  override def typ: Type = new Type {
//    override def typ = $Type
//    override def toUValue(core: Core) = ???
//    override val methods = Map.empty
//  }
//
//  override def realType = typ
//  override def evaluate(mode: EvalMode) = this
//}
