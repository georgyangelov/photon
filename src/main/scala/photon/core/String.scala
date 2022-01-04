package photon.core

import photon.{Arguments, EValue, Location, ULiteral}

object StringType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

object String extends StandardType {
  override val typ = StringType
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "size" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[StringValue]

        IntValue(self.value.length, location)
      }
    }
  )
}

case class StringValue(value: java.lang.String, location: Option[Location]) extends EValue {
  override val typ = String
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.String(value, location)
  override def evaluate = this
}
