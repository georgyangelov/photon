package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.core2.operations.BlockValue
import photon.core2.{BoolValue, FloatValue, IntValue, StringValue}
import photon.{Arguments, EValue, Location, PhotonError, ULiteral, UOperation, UValue}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

case class CallContext(
  interpreter: Interpreter,
//  runMode: RunMode,
//  callStack: Seq[CallStackEntry],
//  callScope: Scope
) {
  def callOrThrow(target: EValue, name: String, args: Arguments[EValue], location: Option[Location]) = {
    target.typ.method(name).getOrElse {
      throw EvalError(s"There is no method $name on ${target.typ}", location)
    }.call(this, args, location)
  }
}

class Interpreter2 {
  private val logger = Logger[Interpreter]

  def evaluate(value: UValue): EValue = value match {
    case ULiteral.Nothing(_) => ???
    case ULiteral.Boolean(value, location) => BoolValue(value, location)
    case ULiteral.Int(value, location) => IntValue(value, location)
    case ULiteral.Float(value, location) => FloatValue(value, location)
    case ULiteral.String(value, location) => StringValue(value, location)

    case UOperation.Block(values, location) =>
      val evalues = values.map(evaluate)

      BlockValue(evalues, location)

    case UOperation.Let(name, value, block, location) => ???
    case UOperation.Reference(name, location) => ???
    case UOperation.Function(fn, location) => ???
    case UOperation.Call(name, arguments, location) => ???
  }
}
