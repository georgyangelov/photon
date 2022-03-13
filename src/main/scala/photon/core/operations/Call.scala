package photon.core.operations

import photon.core.{Core, MethodRunMode, StandardType, StaticType, TypeRoot}
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location, UOperation}

object Call extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class CallValue(name: String, args: Arguments[EValue], location: Option[Location]) extends EValue {
  override val typ = Call
  override def unboundNames =
    args.self.unboundNames ++
    args.positional.flatMap(_.unboundNames).toSet ++
    args.named.values.flatMap(_.unboundNames).toSet

  override def evalMayHaveSideEffects = true // method.traits.contains(MethodTrait.SideEffects)

  override def evalType = {
    method.typeCheck(args) match {
      // TODO: Verify that it can actually be called?
      case StaticType => Some(evaluated.typ)
      case typ => Some(typ)
    }
  }

  override protected def evaluate: EValue = {
    // TODO: Detect if values are fully evaluated or not?
    // method.call(args.map(_.evaluated), location)
    if (method.runMode == MethodRunMode.RunTimeOnly) {
      return this
    }

    method.call(args, location)
  }

//  private lazy val argTypes = args.map { arg => arg.evalType.getOrElse(arg.typ) }

  private lazy val method = {
    val evalType = args.self.evalType
      .getOrElse(args.self.typ)

    evalType.method(name)
      .getOrElse { throw EvalError(s"No method named $name on $evalType (self = ${args.self})", location) }
  }

  override def toUValue(core: Core) = UOperation.Call(name, args.map(_.toUValue(core)), location)
}
