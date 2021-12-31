package photon.core2

import photon.interpreter.CallContext
import photon.{Arguments, EValue, Location}

object FloatType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

object Float extends StandardType {
  override val typ = FloatType
  override val location = None
  override val methods = Map(
    "+" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Float

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[FloatValue]
        val other = args.get(1, "other").assert[FloatValue]

        FloatValue(self.value + other.value, location)
      }
    },

    "-" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Float

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[FloatValue]
        val other = args.get(1, "other").assert[FloatValue]

        FloatValue(self.value - other.value, location)
      }
    }
  )
}

case class FloatValue(value: scala.Double, location: Option[Location]) extends EValue {
  override val typ = Float
}
