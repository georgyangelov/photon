package photon.core.operations

import photon.core.{Core, StandardType, StaticType, TypeRoot}
import photon.interpreter.EvalError
import photon.{Arguments, CannotCallCompileTimeMethodInRunTimeMethod, CannotCallRunTimeMethodInCompileTimeMethod, DelayCall, EValue, EvalMode, Location, MethodType, UOperation}

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

  override protected def evaluate: EValue = {
    // TODO
    val typeCheckedArgs = args

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

//    EValue.context.evalMode match {
//      case EvalMode.CompileTime =>
//        if (method.runMode == MethodRunMode.RunTimeOnly) {
//          return this
//        }
//
//        method.call(args, location)
//
//      // This shouldn't really happen, right? Maybe functions on classes since they won't be eliminated
//      // TODO: Figure this out
//      case EvalMode.Partial(MethodRunMode.CompileTimeOnly) =>
//        if (method.runMode == MethodRunMode.RunTimeOnly) {
//          throw EvalError("Cannot evaluate runtime-only function inside compile-time-only function", location)
//        }
//
//        method.call(args, location)
//
//      case EvalMode.Partial(MethodRunMode.Default)
//         | EvalMode.Partial(MethodRunMode.PreferRunTime)
//         | EvalMode.Partial(MethodRunMode.RunTimeOnly) =>
//        if (method.runMode == MethodRunMode.CompileTimeOnly) {
//          return compileTimeOnlyResult(method.call(args, location))
//        }
//
//        this
//    }
  }

  private def compileTimeOnlyResult(value: EValue): EValue = {
    if (value.isOperation) {
      throw EvalError(s"Could not evaluate compile-time-only method $name on ${args.self.inspect}", location)
    }

    value
  }

  override def finalEval = CallValue(
    name,
    args.map(_.finalEval),
    location
  )

//  private lazy val argTypes = args.map { arg => arg.evalType.getOrElse(arg.typ) }

  private lazy val method = {
    val evalType = args.self.evalType
      .getOrElse(args.self.typ)

    evalType.method(name)
      .getOrElse { throw EvalError(s"No method named $name on $evalType (self = ${args.self})", location) }
  }

  override def toUValue(core: Core) = UOperation.Call(name, args.map(_.toUValue(core)), location)
}
