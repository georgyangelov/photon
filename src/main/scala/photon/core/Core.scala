package photon.core

import photon.{EvalError, Lambda, Location, Parser, Scope, Value}
import photon.core.NativeValue._

import scala.collection.mutable

object Core {
  def nativeValueFor(value: Value): NativeValue = value match {
    case Value.Unknown(location) => error(location)
    case Value.Nothing(location) => error(location)

    case Value.Boolean(_, _) => BoolObject
    case Value.Int(_, _) => IntObject
    case Value.Lambda(_, _) => LambdaObject
    case Value.String(_, _) => StringObject
    case Value.Native(native, _) => native

    case Value.Float(_, location) => error(location)
    case Value.Struct(_, location) => error(location)
    case Value.Operation(_, location) => error(location)
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
      case Some(handler) => Some(
        Core.nativeValueFor(handler).callOrThrowError(
          context,
          "call",
          Vector(handler, Value.Native(ParserObject(parser), None)),
          // TODO
          None
        )
      )
      case None => None
    }
  }

  override def method(
    context: CallContext,
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = {
    name match {
      case "define_macro" => Some(ScalaMethod({ (c, args, l) => defineMacro(args.getString(1), Value.Lambda(args.getLambda(2), l)) }))
      case _ => None
    }
  }

  def defineMacro(name: String, handler: Value): Value = {
    macros.addOne(name, handler)

    Value.Nothing(None)
  }
}
