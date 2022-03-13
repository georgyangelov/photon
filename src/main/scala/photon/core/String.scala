package photon.core

import photon.{Arguments, EValue, Location, ULiteral}

object StringType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

object String extends StandardType {
  override val typ = StringType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "size" -> new Method {
      override val runMode = MethodRunMode.Default

      // TODO: Actually type check arguments
      override def typeCheck(args: Arguments[EValue]) = Int

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[StringValue]

        IntValue(self.value.length, location)
      }
    }
  )
}

case class StringValue(value: java.lang.String, location: Option[Location]) extends EValue {
  override val typ = String
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.String(value, location)
  override def evaluate = this
  override def finalEval = this
}
