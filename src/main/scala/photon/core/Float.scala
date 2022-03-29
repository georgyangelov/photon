package photon.core

import photon.{Arguments, DefaultMethod, EValue, Location, MethodType, ULiteral}
import photon.ArgumentExtensions._

object FloatType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "from" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = ???
      override def run(args: Arguments[EValue], location: Option[Location]) = ???
    }
  )
}

object Float extends StandardType {
  override val typ = FloatType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "+" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType(
          Seq("other" -> Float),
          Float
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[FloatValue]
        val other = args.getEval[FloatValue](1, "other")

        FloatValue(self.value + other.value, location)
      }
    },

    "-" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType(
          Seq("other" -> Float),
          Float
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[FloatValue]
        val other = args.getEval[FloatValue](1, "other")

        FloatValue(self.value - other.value, location)
      }
    }
  )
}

case class FloatValue(value: scala.Double, location: Option[Location]) extends EValue {
  override val typ = Float
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.Float(value, location)
  override def evaluate = this
  override def finalEval = this
}
