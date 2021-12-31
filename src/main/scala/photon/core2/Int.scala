package photon.core2

import photon.interpreter.CallContext
import photon.{Arguments, EValue, Location}

object IntType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map(
    "answer" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) =
        IntValue(42, location)
    }
  )
}

object Int extends StandardType {
  override val typ = IntType
  override val location = None
  override val methods = Map(
    "+" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[IntValue]
        val other = args.get(1, "other").assert[IntValue]

        IntValue(self.value + other.value, location)
      }
    },

    "-" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[IntValue]
        val other = args.get(1, "other").assert[IntValue]

        IntValue(self.value - other.value, location)
      }
    }
  )
}

case class IntValue(value: scala.Int, location: Option[Location]) extends EValue {
  override val typ = Int
}
