package photon.core2

import photon.interpreter.CallContext
import photon.{Arguments, EValue, Location}

object StringType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
}

object String extends StandardType {
  override val typ = StringType
  override val location = None
  override val methods = Map(
    "size" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(context: CallContext, args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[StringValue]

        IntValue(self.value.length, location)
      }
    }
  )
}

case class StringValue(value: java.lang.String, location: Option[Location]) extends EValue {
  override val typ = String
}
