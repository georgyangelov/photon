package photon.core

import photon.{Arguments, EValue, Location, ULiteral}

object BoolType extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override val methods = Map.empty

  override def toUValue(core: Core) = inconvertible
}

object Bool extends StandardType {
  override val typ = BoolType
  override val location = None

  override def toUValue(core: Core) = core.referenceTo(Bool, location)

  override val methods = Map(
    // TODO: Short-circuiting
    "and" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Bool

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[BoolValue]
        val other = args.get(1, "other").assert[BoolValue]

        BoolValue(self.value && other.value, location)
      }
    },

    "or" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Actually type check arguments
      override def typeCheck(argumentTypes: Arguments[Type]) = Bool

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.assert[BoolValue]
        val other = args.get(1, "other").assert[BoolValue]

        BoolValue(self.value || other.value, location)
      }
    }
  )
}

case class BoolValue(value: scala.Boolean, location: Option[Location]) extends EValue {
  override val typ = Bool
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def evaluate = this
  override def toUValue(core: Core) = ULiteral.Boolean(value, location)
}
