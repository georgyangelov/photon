package photon.core

import photon.core.operations.CallValue
import photon.{Arguments, EValue, Location}

object OptionalRootType extends StandardType {
  override val location = None
  override def unboundNames = Set.empty
  override def typ = TypeRoot
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "call" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(args: Arguments[EValue]) = Optional(args.positional.head, None).typ
      override def call(args: Arguments[EValue], location: Option[Location]) =
        Optional(args.positional.head, location)
    }
  )
}
object OptionalRoot extends StandardType {
  override def typ = OptionalRootType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class OptionalT(optional: Optional) extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = optional.location
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "empty" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = optional
      override def call(args: Arguments[EValue], location: Option[Location]) =
        OptionalValue(optional, None, location)
    },

    "of" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = optional
      override def call(args: Arguments[EValue], location: Option[Location]) =
        OptionalValue(optional, Some(args.positional.head), location)
    }
  )
}
case class Optional(innerType: EValue, location: Option[Location]) extends StandardType {
  override lazy val typ = OptionalT(this)
  override def unboundNames = innerType.unboundNames
  override def toUValue(core: Core) =
    CallValue(
      "call",
      Arguments.positional(OptionalRoot, Seq(innerType)),
      location
    ).toUValue(core)

  override val methods = Map(
    "getAssert" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = ???
      override def call(args: Arguments[EValue], location: Option[Location]) = ???
    }
  )
}

case class OptionalValue(typ: Optional, innerValue: Option[EValue], location: Option[Location]) extends EValue {
  override def evalType = None
  override def unboundNames = innerValue.map(_.unboundNames).getOrElse(Set.empty)
  override def evalMayHaveSideEffects = false
  override def toUValue(core: Core) =
    if (innerValue.isDefined) {
      CallValue(
        "of",
        Arguments.positional(typ, Seq(innerValue.get)),
        location
      ).toUValue(core)
    } else {
      CallValue(
        "empty",
        Arguments.empty(typ),
        location
      ).toUValue(core)
    }

  override protected def evaluate: EValue = this
}
