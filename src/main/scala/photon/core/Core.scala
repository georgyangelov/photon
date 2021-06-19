package photon.core

import photon.frontend.{ASTValue, Parser, StaticScope}
import photon.{Arguments, BoundFunction, EvalError, FunctionTrait, Location, Scope, Struct, TypeObject, Value, Variable, VariableName}
import photon.core.NativeValue._

import scala.collection.mutable

object Core {
  def nativeValueFor(value: Value): NativeValue = value match {
    case Value.Unknown(location) => error(location)
    case Value.Nothing(location) => error(location)

    case Value.Boolean(_, _) => BoolObject
    case Value.Int(_, _, _) => IntObject
    case Value.BoundFunction(boundFn, _) => nativeValueFor(boundFn)
    case Value.String(_, _) => StringObject
    case Value.Native(native, _) => native
    case Value.Struct(struct, _) => nativeValueFor(struct)

    case Value.Float(_, location) => error(location)
    case Value.Operation(_, location) => error(location)
  }

  def nativeValueFor(boundFn: BoundFunction): NativeValue = BoundFunctionObject(boundFn)
  def nativeValueFor(struct: Struct): NativeValue = StructObject(struct)

  def isSameObject(a: Value, b: Value): Boolean = {
    (a, b) match {
      case (Value.Nothing(_), Value.Nothing(_)) => true
      case (Value.Boolean(one, _), Value.Boolean(two, _)) => one == two
      case (Value.Int(one, _, _), Value.Int(two, _, _)) => one == two
      case (Value.Float(one, _), Value.Float(two, _)) => one == two
      case (Value.String(one, _), Value.String(two, _)) => one == two
      case (Value.Native(one, _), Value.Native(two, _)) => one == two
      case (Value.Struct(one, _), Value.Struct(two, _)) => one == two
      case (Value.BoundFunction(one, _), Value.BoundFunction(two, _)) => one == two
      case (Value.Operation(one, _), Value.Operation(two, _)) => one == two
      case _ => false
    }
  }

  private def error(l: Option[Location]): Nothing = {
    throw EvalError("Cannot call methods on this object (yet)", l)
  }
}

object StructRoot extends NativeObject(Map(
  "call" -> ScalaVarargMethod((context, args, l) => {
    if (args.positional.size != 1) {
      throw EvalError("Cannot pass positional arguments to Struct constructor", l)
    }

    Value.Struct(Struct(args.named), l)
  }, Set(FunctionTrait.Partial, FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure))
))

object IntRootParams {
  val Self: Parameter = Parameter(0, "self")
  val Other: Parameter = Parameter(1, "other")
}

object IntRoot extends NativeObject(Map(
  "assignableFrom" -> ScalaMethod(
    MethodOptions(Seq(IntRootParams.Self, IntRootParams.Other)),
    (_, args, l) => Value.Boolean(Core.isSameObject(args.get(IntRootParams.Self), args.get(IntRootParams.Other)), l)
  )
))

object StringRootParams {
  val Self: Parameter = Parameter(0, "self")
  val Other: Parameter = Parameter(1, "other")
}

object StringRoot extends NativeObject(Map(
  "assignableFrom" -> ScalaMethod(
    MethodOptions(Seq(StringRootParams.Self, StringRootParams.Other)),
    (_, args, l) => Value.Boolean(args.get(StringRootParams.Self) == args.get(StringRootParams.Other), l)
  )
))

object CoreParams {
  val Self: Parameter = Parameter(0, "self")

  val DefineMacroName: Parameter = Parameter(1, "name")
  val DefineMacroLambda: Parameter = Parameter(2, "lambda")

  val TypeCheckValue: Parameter = Parameter(1, "value")
  val TypeCheckType: Parameter = Parameter(2, "type")
}

class Core extends NativeValue {
  val macros: mutable.TreeMap[String, Value] = mutable.TreeMap.empty
  val rootScope: Scope = Scope.newRoot(Seq(
    new Variable(new VariableName("Core"), Value.Native(this, None)),
    new Variable(new VariableName("Struct"), Value.Native(StructRoot, None)),
    new Variable(new VariableName("Int"), Value.Native(IntRoot, None)),
    new Variable(new VariableName("String"), Value.Native(StringRoot, None))
  ))

  val staticRootScope = StaticScope.newRoot(rootScope.variables.keys.toSeq)

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[ASTValue] = {
    None
//    macros.get(name) match {
//      case Some(handler) => Some(
//        Core.nativeValueFor(handler).callOrThrowError(
//          context,
//          "call",
//          Arguments(Seq(handler, Value.Native(ParserObject(parser), None)), Map.empty),
//          // TODO
//          None
//        )
//      )
//      case None => None
//    }
  }

  override def method(
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = {
    name match {
      // TODO: Extract this to not create method instances every time
      case "define_macro" => Some(
        ScalaMethod(
          MethodOptions(Seq(CoreParams.Self, CoreParams.DefineMacroName, CoreParams.DefineMacroLambda)),
          { (_, args, l) => defineMacro(
            args.getString(CoreParams.DefineMacroName),
            Value.BoundFunction(args.getFunction(CoreParams.DefineMacroLambda), l)
          ) }
        )
      )

      // TODO: Extract this to not create method instances every time
      case "typeCheck" => Some(
        ScalaMethod(
          MethodOptions(Seq(CoreParams.Self, CoreParams.TypeCheckValue, CoreParams.TypeCheckType)),
          { (context, args, l) =>
            val value = args.get(CoreParams.TypeCheckValue)
            val expectedTypeValue = args.get(CoreParams.TypeCheckType)

            val actualTypeValue = value.typeObject match {
              case Some(TypeObject.Native(native)) => Value.Native(native, value.location)
              case Some(TypeObject.Struct(struct)) => Value.Struct(struct, value.location)
              case None => throw EvalError("Bad state - typeCheck called on value but value does not have an inferred type", l)
            }

            val areTypesCompatible = Core.nativeValueFor(actualTypeValue).callOrThrowError(
              context,
              "assignableFrom",
              Arguments(Seq(actualTypeValue, expectedTypeValue), Map.empty),
              l
            ).asBool

            if (areTypesCompatible) {
              // TODO: New Value variant which changes the type?
              // value.withType(typeValue)
              value
            } else {
              // TODO: Type objects should contain name function
              throw EvalError(s"Incompatible types. $actualTypeValue is not assignable to $expectedTypeValue", l)
            }
          }
        )
      )

      case _ => None
    }
  }

  def defineMacro(name: String, handler: Value): Value = {
    macros.addOne(name, handler)

    Value.Nothing(None)
  }

//  private def typeOf(value: Value): Option[TypeObject] = {
//    value match {
//      case Value.Unknown(_) => None
//      case Value.Nothing(_) => None
//
//      // TODO
//      case Value.Boolean(value, _) => None
//      case Value.Int(value, _) => Some(TypeObject.Native(IntRoot))
//      case Value.Float(value, _) => None
//      case Value.String(value, _) => Some(TypeObject.Native(StringRoot))
//      case Value.Native(native, _) => None
//      case Value.Struct(value, _) => None
//
//      // TODO
//      case Value.Operation(operation, _) => None
//      case Value.Lambda(value, _) => None
//    }
//  }
}
