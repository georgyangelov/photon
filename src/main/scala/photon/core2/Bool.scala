package photon.core2

import photon.interpreter.CallContext
import photon.{Arguments, EValue, Location}

object BoolType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

object Bool extends StandardType {
  override val typ = BoolType
  override val location = None
  override val methods = Map(
    // TODO: Short-circuiting
    "and" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Bool

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[BoolValue]
        val other = args.get(1, "other").assert[BoolValue]

        BoolValue(self.value && other.value, location)
      }
    },

    "or" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Bool

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[BoolValue]
        val other = args.get(1, "other").assert[BoolValue]

        BoolValue(self.value || other.value, location)
      }
    }
  )
}

case class BoolValue(value: scala.Boolean, location: Option[Location]) extends EValue {
  override val typ = Bool
}
