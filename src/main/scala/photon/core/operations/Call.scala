package photon.core.operations

import photon.core.{Core, MethodTrait, StandardType, TypeRoot}
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location, UOperation}

object Call extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class CallValue(name: String, args: Arguments[EValue], location: Option[Location]) extends EValue {
  override val typ = Call
  override def evalMayHaveSideEffects = method.traits.contains(MethodTrait.SideEffects)

  override def evalType = Some(method.typeCheck(args))
  override protected def evaluate: EValue = {
    // TODO: Detect if values are fully evaluated or not
    // method.call(args.map(_.evaluated), location)
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
