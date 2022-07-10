package photon.interpreter

import photon.base._

sealed trait EvalMode
object EvalMode {
  // Running code during runtime
  case object RunTime extends EvalMode

  // Running compile-time-only code
  case object CompileTimeOnly extends EvalMode

  // Partially evaluating code in a default function
  case object Partial extends EvalMode

  // Partially evaluating code in a runtime-only function
  case object PartialRunTimeOnly extends EvalMode

  // Partially evaluating code in a prefer-runtime function
  case object PartialPreferRunTime extends EvalMode
}


class Interpreter {
  def evaluate(value: UValue, scope: Scope, mode: EvalMode): EValue = value match {
    case UValue.Bool(value, location) => Value.Object(value, typ)

    case UValue.Block(values, location) =>
      val evalues = values.map(evaluate(_, scope, mode))

      if (evalues.length == 1) {
        evalues.head
      } else {
        val eblock = UValue.Block(evalues.map(_.value), location)

        EValue(eblock, evalues.last.typ)
      }

    case UValue.Let(name, value, body, location) =>
      val unknown = UValue.Unknown("Cannot self-reference directly", location)
      val innerScope = scope.newChild(Seq(name -> EValue(unknown, unknown)))

      val evalue = evaluate(value, innerScope, mode)
      innerScope.dangerouslySetValue(name, evalue)

      val ebody = evaluate(body, innerScope, mode)
      ebody.value match {
        // Inline if the body is a direct reference to this let value
        case UValue.Reference(refName, _) if refName == name => evalue
        // case _ if ebody.unboundNames.contains(name) => $Let.Value(name, evalue, ebody, location)
        // case _ => UValue.Block(Seq(value, body), location)
        case _ =>
          EValue(
            UValue.Let(name, evalue.value, ebody.value, location),
            ebody.typ
          )
      }

    case UValue.Reference(name, location) =>
      val referencedValue = scope.find(name)
        .getOrElse { throw EvalError(s"Invalid reference to $name", location) }

      mode match {
        case EvalMode.CompileTimeOnly |
             EvalMode.RunTime => referencedValue

        // If it's in a partial mode => it's not required (yet)
        case EvalMode.Partial |
             EvalMode.PartialPreferRunTime |
             EvalMode.PartialRunTimeOnly => EValue(value, referencedUValue.typ)
      }

    case UValue.Function(fn, location) => ???

    case UValue.Call(name, arguments, location) => ???

  }
}
