package photon.core

import photon.{EvalError, Location, Value}

object Core {
  def nativeObjectFor(value: Value): NativeObject = value match {
    case Value.Unknown(location) => error(location)
    case Value.Nothing(location) => error(location)
    case Value.Boolean(value, location) => error(location)

    case Value.Int(_, _) => IntObject
    case Value.Lambda(_, _) => LambdaObject

    case Value.Float(value, location) => error(location)
    case Value.String(value, location) => error(location)
    case Value.Struct(value, location) => error(location)
    case Value.Operation(operation, location) => error(location)
  }

  private def error(l: Option[Location]): Nothing = {
    throw EvalError("Cannot call methods on this object (yet)", l)
  }
}
