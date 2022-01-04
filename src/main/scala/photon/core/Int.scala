package photon.core

import photon.{Arguments, EValue, Location, ULiteral}

object IntType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "answer" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) =
        IntValue(42, location)
    }
  )
}

object Int extends StandardType {
  override val typ = IntType
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "+" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[IntValue]
        val other = args.get(1, "other").assert[IntValue]

        IntValue(self.value + other.value, location)
      }
    },

    "-" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[IntValue]
        val other = args.get(1, "other").assert[IntValue]

        IntValue(self.value - other.value, location)
      }
    }
  )
}

case class IntValue(value: scala.Int, location: Option[Location]) extends EValue {
  override val typ = Int
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.Int(value, location)
  override def evaluate = this
}
