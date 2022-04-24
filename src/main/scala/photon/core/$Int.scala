package photon.core

import photon.base._

object $Int extends Type {
  val static = new Type {
    override def typ: Type = $Type
    override val methods = Map(
      "answer" -> new DefaultMethod {
        override val signature = MethodSignature(Seq.empty, $Int)
        override def apply(args: MethodType, location: Option[Location]) = IntValue(42, location)
      }
    )
  }

  override def typ = static

  override val methods = Map(
    "+" -> new DefaultMethod {
      override val signature = MethodSignature(
        Seq("other" -> Pattern.SpecificValue($Int)),
        $Int
      )

      override def apply(args: MethodType, location: Option[Location]) = {
        val self = args.selfEval[IntValue]
        val other = args.getEval[IntValue]("other")

        IntValue(self.value + other.value, location)
      }
    }
  )
}

case class IntValue(value: Int, location: Option[Location]) extends RealEValue {
  override def typ: Type = $Int
  override def unboundNames = Set.empty
}