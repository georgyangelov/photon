package photon.core

import photon.{Arguments, DefaultMethod, EValue, Location, ULiteral, MethodType}
import photon.ArgumentExtensions._

object IntType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "answer" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, Int)

      override def run(args: Arguments[EValue], location: Option[Location]) =
        IntValue(42, location)
    }
  )
}

object Int extends StandardType {
  override val typ = IntType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "+" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq("other" -> Int),
          Int
        )

      override def run(args: Arguments[EValue], location: Option[Location]): EValue = {
        val self = args.selfEvalInlined[IntValue]
        val other = args.getEvalInlined[IntValue](1, "other")

        IntValue(self.value + other.value, location)
      }
    },

    "-" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq("other" -> Int),
          Int
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEvalInlined[IntValue]
        val other = args.getEvalInlined[IntValue](1, "other")

        IntValue(self.value - other.value, location)
      }
    },

    "*" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq("other" -> Int),
          Int
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEvalInlined[IntValue]
        val other = args.getEvalInlined[IntValue](1, "other")

        IntValue(self.value * other.value, location)
      }
    },

    "==" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq("other" -> Int),
          Bool
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEvalInlined[IntValue]
        val other = args.getEvalInlined[IntValue](1, "other")

        BoolValue(self.value == other.value, location)
      }
    }
  )
}

case class IntValue(value: scala.Int, location: Option[Location]) extends EValue {
  override val typ = Int
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.Int(value, location)
  override def evaluate = this
  override def finalEval = this
}
