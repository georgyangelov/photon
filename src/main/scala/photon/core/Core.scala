package photon.core

import photon.{Arguments, EvalError, Lambda, Location, Parser, Scope, Struct, Value}
import photon.core.NativeValue._

import scala.collection.mutable

object Core {
  def nativeValueFor(value: Value): NativeValue = value match {
    case Value.Unknown(location) => error(location)
    case Value.Nothing(location) => error(location)

    case Value.Boolean(_, _) => BoolObject
    case Value.Int(_, _) => IntObject
    case Value.Lambda(lambda, _) => LambdaObject(lambda)
    case Value.String(_, _) => StringObject
    case Value.Native(native, _) => native
    case Value.Struct(struct, location) => StructObject(struct, location)

    case Value.Float(_, location) => error(location)
    case Value.Operation(_, location) => error(location)
  }

  def nativeValueFor(lambda: Lambda): NativeValue = LambdaObject(lambda)

  private def error(l: Option[Location]): Nothing = {
    throw EvalError("Cannot call methods on this object (yet)", l)
  }
}

object CoreParameters {
  val DefineMacroName: Parameter = Parameter(1, "name")
  val DefineMacroLambda: Parameter = Parameter(2, "lambda")
}

import CoreParameters._

object StructRoot extends NativeObject(Map(
  "call" -> ScalaVarargMethod((context, args, l) => {
    if (args.positional.size != 1) {
      throw EvalError("Cannot pass positional arguments to Struct constructor", l)
    }

    Value.Struct(Struct(args.named), l)
  }, withSideEffects = false)
))

class Core extends NativeValue {
  val macros: mutable.TreeMap[String, Value] = mutable.TreeMap.empty
  val rootScope: Scope = Scope(None, Map(
    "Core" -> Value.Native(this, None),
    "Struct" -> Value.Native(StructRoot, None)
  ))

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[Value] = {
    macros.get(name) match {
      case Some(handler) => Some(
        Core.nativeValueFor(handler).callOrThrowError(
          context,
          "call",
          Arguments(Seq(handler, Value.Native(ParserObject(parser), None)), Map.empty),
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
      case "define_macro" => Some(
        ScalaMethod(
          MethodOptions(parameters = Seq(DefineMacroName, DefineMacroLambda)),
          { (c, args, l) => defineMacro(args.getString(DefineMacroName), Value.Lambda(args.getLambda(DefineMacroLambda), l)) }
        )
      )
      case _ => None
    }
  }

  def defineMacro(name: String, handler: Value): Value = {
    macros.addOne(name, handler)

    Value.Nothing(None)
  }
}
