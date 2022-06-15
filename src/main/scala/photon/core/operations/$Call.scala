package photon.core.operations

import photon.Core
import photon.base._
import photon.core._
import photon.frontend.UOperation

object $Call extends Type {
  override def typ = $Type
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty

  case class Value(name: String, args: Arguments[EValue], location: Option[Location]) extends EValue {
    override def typ = $Call
    override def unboundNames = args.values.flatMap(_.unboundNames).toSet

    override def toUValue(core: Core) = UOperation.Call(name, args.map(_.toUValue(core)), location)

    override def realType = {
      val returnType = callSpec.returnType

      if (returnType == $AnyStatic)
        evaluated(EvalMode.CompileTimeOnly).typ
      else
        returnType
    }

    override def evaluate(mode: EvalMode) = {
      try {
        method.call(callSpec, location)
      } catch {
        case DelayCall =>
          // TODO: This should be correct, right? Or not? What about self-references here?
          val evaluatedArgs = callSpec.args.map(_.evaluated)

          $Call.Value(name, evaluatedArgs, location)

        case CannotCallCompileTimeMethodInRunTimeMethod =>
          throw EvalError(s"Cannot call compile-time-only method $name inside of runtime-only method", location)

        case CannotCallRunTimeMethodInCompileTimeMethod =>
          throw EvalError(s"Cannot call run-time-only method $name inside of compile-time-only method", location)
      }
    }

    private lazy val method = {
      val typ = args.self.realType

      typ.method(name)
        .getOrElse { throw EvalError(s"No method named $name on $typ (self = ${args.self})", location) }
    }

    private lazy val callSpec: CallSpec = method.signature.specialize(args)
  }
}
