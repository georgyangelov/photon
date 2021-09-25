package photon.core

import photon.New.TypeObject
import photon.frontend.{ASTValue, Parser, StaticScope, ValueToAST}
import photon.{AnyType, ArgumentType, Arguments, Location, New, PureValue, RealValue, Scope, TypeType,
  Value, Variable, VariableName}
import photon.interpreter.{EvalError, Unbinder}

import scala.collection.mutable
import photon.core.Conversions._

object Core {
//  def nativeValueFor(realValue: RealValue): NativeValue = realValue match {
//    case PureValue.Nothing(location) => error(location)
//    case PureValue.Boolean(_, _) => BoolObject
//    case PureValue.Int(_, _) => IntObject
//    case PureValue.Float(_, location) => error(location)
//    case PureValue.String(_, _) => StringObject
//    case PureValue.Native(native, _) => native
//
//    case fn: BoundValue.Function => nativeValueFor(fn)
//    case obj: BoundValue.Object => ObjectObject(obj)
//  }
//
//  def nativeValueFor(boundFn: BoundValue.Function): NativeValue = BoundFunctionObject(boundFn)
//
//  private def error(l: Option[Location]): Nothing = {
//    throw EvalError("Cannot call methods on this object (yet)", l)
//  }

  // TODO: Find a better place for this
  def callOrThrowError(
    value: RealValue,
    context: CallContext,
    name: String,
    args: Arguments[RealValue],
    location: Option[Location]
  ): Value = {
    value.typeObject.getOrElse {
      throw EvalError(s"Cannot call method $name on ${value.toString} because it has an unknown type", location)
    }.instanceMethod(name) match {
      case Some(method) => method.call(context, args.withSelf(value), location)
      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
    }
  }

  def callOrThrowError(
    typeObject: TypeObject,
    context: CallContext,
    name: String,
    args: Arguments[RealValue],
    location: Option[Location]
  ): Value = {
    typeObject.method(name) match {
      case Some(method) => method.call(context, args.withSelf(typeObject.toValue), location)
      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
    }
  }
}

object StringRootParams {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")
}

object CoreParams {
  val Self = Parameter(0, "self")

  val DefineMacroName = Parameter(1, "name")
  val DefineMacroLambda = Parameter(2, "lambda")

  val TypeCheckValue = Parameter(1, "value")
  val TypeCheckType = Parameter(2, "type")
}

class Core extends New.TypeObject {
  val macros: mutable.TreeMap[String, RealValue] = mutable.TreeMap.empty

  val objectRootVar = new Variable(new VariableName("Object"), Some(ObjectType.toValue))

  val rootScope: Scope = Scope.newRoot(Seq(
    // TODO: Core should have a type
    new Variable(new VariableName("Core"),   Some(this.toValue)),
    new Variable(new VariableName("Type"),   Some(TypeType.toValue)),
    objectRootVar,
    new Variable(new VariableName("Int"),    Some(IntType.toValue)),
    new Variable(new VariableName("Float"),  Some(FloatType.toValue)),
    new Variable(new VariableName("String"), Some(StringType.toValue)),
    new Variable(new VariableName("List"),   Some(ListType.toValue))
  ))

  val staticRootScope = StaticScope.fromScope(rootScope)
  val unbinder = new Unbinder(this)

  override val methods = Map(
    "defineMacro" -> new New.CompileTimeOnlyMethod {
      override val name = "defineMacro"
      override val arguments = Seq(
        ArgumentType("name", StringType),
        ArgumentType("handler", FunctionType(Seq(ArgumentType("parser", ParserType)), AnyType)),
      )
      override val returns = NothingType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        defineMacro(
          args.getString(CoreParams.DefineMacroName),
          // TODO: Support more value types as macro handlers (e.g. Structs or Natives)
          args.getFunction(CoreParams.DefineMacroLambda)
        )

        PureValue.Nothing(location)
      }
    },

//    "typeCheck" -> TypeCheckMethod(this)
  )
  override val instanceMethods = Map.empty

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[ASTValue] = {
    macros.get(name) match {
      case Some(handler) =>
        val parserValue = PureValue.Native(ParserObject(parser), None)
        val valueResult = Core.callOrThrowError(
          handler.typeObject.get,
          context,
          "call",
          Arguments(Some(handler), Seq(parserValue), Map.empty),
          handler.location
        )

        val unboundResult = unbinder.unbind(valueResult, rootScope)

        // TODO: Normalize `name` so it doesn't have any symbols not allowed in variable names
        // TODO: Make the name completely unique, right now it's predictable
        // TODO: Can the names from one call of a macro collide with another one? Maybe we need
        //       the objectids as part of the rename?
        val astResult = ValueToAST.transformRenamingAll(unboundResult, name)

        Some(astResult)

      case None => None
    }
  }

  def defineMacro(name: String, handler: RealValue): Unit =
    macros.addOne(name, handler)
}

//private case class TypeCheckMethod(private val core: Core) extends NativeMethod {
//  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Partial)
//
//  override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//    partialCall(context, args.asInstanceOf[Arguments[Value]], location)
//
//  override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = {
//    ???
////    val value = args.get(Parameter(1, "value"))
////    val typeValue = args.get(Parameter(2, "type")).realValue
////
////    if (value.realValue)
//  }
//}
