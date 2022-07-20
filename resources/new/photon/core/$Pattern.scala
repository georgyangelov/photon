package photon.core

import photon._
import photon.base._
import photon.core.operations._
import photon.frontend._

object $Pattern extends Type {
  override def typ = $Type
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
//    "match" -> new CompileTimeOnlyMethod {
//      override val signature = MethodSignature.of(
//        Seq("value" -> $Pattern.SpecificValue($AnyStatic, None)),
//        ???
//      )
//      override def apply(args: CallSpec, location: Option[Location]): EValue = ???
//    }
  )

  sealed trait Value extends EValue {
    def bindings: Seq[EVarName]
    override def toUValue(core: Core): UPattern
  }

  case class SpecificValue(value: EValue, location: Option[Location]) extends Value {
    override def bindings = Seq.empty
    override def unboundNames = value.unboundNames
    override def toUValue(core: Core) = ???
    override def typ = $Pattern
    override def realType = $Pattern
    override def evaluate(mode: EvalMode): EValue = this
  }

  case class Binding(name: EVarName, location: Option[Location]) extends Value {
    override def bindings = Seq(name)
    override def unboundNames = Set.empty
    override def toUValue(core: Core) = ???
    override def typ = $Pattern
    override def realType = $Pattern
    override def evaluate(mode: EvalMode): EValue = this
  }

  case class Call(target: EValue, name: String, args: ArgumentsWithoutSelf[$Pattern.Value], location: Option[Location]) extends Value {
    override def bindings = args.argValues.flatMap(_.bindings)
    override def unboundNames = target.unboundNames ++ args.argValues.flatMap(_.unboundNames)
    override def toUValue(core: Core) = ???
    override def typ = $Pattern
    override def realType = $Pattern
    override def evaluate(mode: EvalMode): EValue = this
  }
}

object $MatchResult extends Type {
  override def typ = new Type {
    override def typ = $Type
    override def toUValue(core: Core) = inconvertible
    override val methods = Map(
      // TODO: Implement this by creating an instance of an anonymous class
      "of" -> new Method {
        override val signature = MethodSignature.any($MatchResult)
        override def call(args: CallSpec, location: Option[Location]) = Value(args.bindings, location)
      }
    )
  }
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(

  )

  case class Value(values: Seq[(String, EValue)], location: Option[Location]) extends RealEValue {
    override def typ = $MatchResult
    override def unboundNames = values.map(_._2).flatMap(_.unboundNames).toSet
    override def toUValue(core: Core) = $Call.Value(
      "of",
      // TODO: Do we need to preserve the order of values here?
      Arguments.named($MatchResult, values.toMap),
      location
    ).toUValue(core)
  }
}