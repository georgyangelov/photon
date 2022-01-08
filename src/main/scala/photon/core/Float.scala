package photon.core

import photon.{Arguments, EValue, Location, ULiteral}

object FloatType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty
  override def toUValue(core: Core) = inconvertible
}

object Float extends StandardType {
  override val typ = FloatType
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "+" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(args: Arguments[EValue]) = Float

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[FloatValue]
        val other = args.get(1, "other").evalAssert[FloatValue]

        FloatValue(self.value + other.value, location)
      }
    },

    "-" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(args: Arguments[EValue]) = Float

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[FloatValue]
        val other = args.get(1, "other").evalAssert[FloatValue]

        FloatValue(self.value - other.value, location)
      }
    }
  )
}

case class FloatValue(value: scala.Double, location: Option[Location]) extends EValue {
  override val typ = Float
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.Float(value, location)
  override def evaluate = this
}
