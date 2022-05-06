package photon.core

import photon.Core
import photon.base._
import photon.frontend.{ULiteral, UValue}

object $Int extends Type {
  val static = new Type {
    override def typ: Type = $Type
    override val methods = Map(
      "answer" -> new DefaultMethod {
        override val signature = MethodSignature(Seq.empty, $Int)
        override def apply(args: CallSpec, location: Option[Location]) = $Int.Value(42, location)
      }
    )
    override def toUValue(core: Core) = inconvertible
  }

  override def typ = static
  override def toUValue(core: Core): UValue = core.referenceTo(this, location)

  override val methods = Map(
    "+" -> new DefaultMethod {
      override val signature = MethodSignature(
        Seq("other" -> Pattern.SpecificValue($Int)),
        $Int
      )

      override def apply(args: CallSpec, location: Option[Location]) = {
        val self = args.selfEval[$Int.Value]
        val other = args.getEval[$Int.Value]("other")

        $Int.Value(self.value + other.value, location)
      }
    }
  )

  case class Value(value: Int, location: Option[Location]) extends RealEValue {
    override def typ = $Int
    override def unboundNames = Set.empty
    override def toUValue(core: Core) = ULiteral.Int(value, location)
  }
}
