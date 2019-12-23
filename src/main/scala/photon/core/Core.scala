package photon.core

import photon.Parser.MacroHandler
import photon.{EvalError, Lambda, Location, Parser, Scope, Value}
import photon.core.NativeValue._

import scala.collection.mutable

object Core {
  def nativeValueFor(value: Value): NativeValue = value match {
    case Value.Unknown(location) => error(location)
    case Value.Nothing(location) => error(location)
    case Value.Boolean(value, location) => error(location)

    case Value.Int(_, _) => IntObject
    case Value.Lambda(_, _) => LambdaObject
    case Value.Native(native, _) => native

    case Value.Float(value, location) => error(location)
    case Value.String(value, location) => error(location)
    case Value.Struct(value, location) => error(location)
    case Value.Operation(operation, location) => error(location)
  }

  def nativeValueFor(lambda: Lambda): NativeValue = LambdaObject

  private def error(l: Option[Location]): Nothing = {
    throw EvalError("Cannot call methods on this object (yet)", l)
  }
}

class Core extends NativeValue {
  val macros: mutable.TreeMap[String, Value] = mutable.TreeMap.empty
  val rootScope: Scope = Scope(None, Map(
    "Core" -> Value.Native(this, None)
  ))

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[Value] = {
    macros.get(name) match {
      case Some(handler) => Some(Core.nativeValueFor(handler).call(
        context,
        "call",
        Vector(handler, Value.Native(ParserObject(parser), None)),
        // TODO
        None
      ))
      case None => None
    }
  }

  override def call(
    context: CallContext,
    name: String,
    args: Seq[Value],
    location: Option[Location]
  ): Value = {
    name match {
      case "define_macro" => defineMacro(args.getString(1), Value.Lambda(args.getLambda(2), location))
      case _ => super.call(context, name, args, location)
    }
  }

  def defineMacro(name: String, handler: Value): Value = {
    macros.addOne(name, handler)

    Value.Nothing(None)
  }
}
