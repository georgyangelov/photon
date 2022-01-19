package photon.core

import photon.compiler.CompilerContext
import photon.{Arguments, EValue, Location, ULiteral}

object FloatType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override val methods = Map.empty
  override def toUValue(core: Core) = inconvertible
  override def compile(output: CompilerContext): Unit = uncompilable
}

object Float extends StandardType {
  override val typ = FloatType
  override def unboundNames = Set.empty
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
  override def compile(output: CompilerContext): Unit = uncompilable
}

case class FloatValue(value: scala.Double, location: Option[Location]) extends EValue {
  override val typ = Float
  override def unboundNames = Set.empty
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override def toUValue(core: Core) = ULiteral.Float(value, location)
  override def evaluate = this
  override def compile(output: CompilerContext): Unit = output.appendValue(value.toString)
}
