package photon.core.operations

import photon._
import photon.core._
import photon.interpreter.EvalError

object Call extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class CallValue(name: String, args: Arguments[EValue], location: Option[Location]) extends EValue {
  override def isOperation = true
  override val typ = Call
  override def unboundNames =
    args.self.unboundNames ++
    args.positional.flatMap(_.unboundNames).toSet ++
    args.named.values.flatMap(_.unboundNames).toSet

  override def evalMayHaveSideEffects = true

  override def evalType = {
    method.specialize(args, location).returnType match {
      // TODO: Verify that it can actually be called?
      case StaticType => Some(evaluated(EvalMode.CompileTimeOnly).typ)
      case typ => Some(typ)
    }
  }

  // TODO: Are we safe from double-wrapping?
  private def typeCheck(value: EValue, typ: EValue): EValue = {
    CallValue(
      "typeCheck",
      Arguments.positional(
        EValue.context.core,
        Seq(value, typ)
      ),
      value.location
    )
  }

  override protected def evaluate: EValue = {
    // TODO: Check that all arguments are present
    val methodType = method.specialize(args, location)
    val paramNames = methodType.argTypes.map(_._1)
    val typesByName = methodType.argTypes.toMap

    val typeCheckedArgs = args.mapWithNames(paramNames) { (argName, value) =>
      EValue.context.core.typeCheck(value, typesByName(argName).assertType, location)
    }

    try {
      method.call(typeCheckedArgs, location)
    } catch {
      case DelayCall =>
        // TODO: This should be correct, right? Or not? What about self-references here?
        val evaluatedArgs = typeCheckedArgs.map(_.evaluated)

        CallValue(name, evaluatedArgs, location)

      case CannotCallCompileTimeMethodInRunTimeMethod =>
        throw EvalError(s"Cannot call compile-time-only method $name inside of runtime-only method", location)

      case CannotCallRunTimeMethodInCompileTimeMethod =>
        throw EvalError(s"Cannot call run-time-only method $name inside of compile-time-only method", location)
    }
  }

  override def finalEval = CallValue(
    name,
    args.map(_.finalEval),
    location
  )

  private lazy val method = {
    val evalType = args.self.evalType
      .getOrElse(args.self.typ)

    evalType.method(name)
      .getOrElse { throw EvalError(s"No method named $name on $evalType (self = ${args.self})", location) }
  }

  override def toUValue(core: Core) = UOperation.Call(name, args.map(_.toUValue(core)), location)
}
