package photon.core

import photon.frontend.{ASTValue, Parser, StaticScope, ValueToAST}
import photon.{Arguments, BoundValue, FunctionTrait, Location, PureValue, RealValue, Scope, Value, Variable, VariableName}
import photon.interpreter.{EvalError, Unbinder}

import scala.collection.mutable
import photon.core.Conversions._

object Core {
  def nativeValueFor(realValue: RealValue): NativeValue = realValue match {
    case PureValue.Nothing(location) => error(location)
    case PureValue.Boolean(_, _) => BoolObject
    case PureValue.Int(_, _) => IntObject
    case PureValue.Float(_, location) => error(location)
    case PureValue.String(_, _) => StringObject
    case PureValue.Native(native, _) => native

    case fn: BoundValue.Function => nativeValueFor(fn)
    case obj: BoundValue.Object => ObjectObject(obj)
  }

  def nativeValueFor(boundFn: BoundValue.Function): NativeValue = BoundFunctionObject(boundFn)

  private def error(l: Option[Location]): Nothing = {
    throw EvalError("Cannot call methods on this object (yet)", l)
  }
}

object ObjectRoot extends NativeObject(Map(
  "call" -> new {} with PartialMethod {
    override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
      partialCall(context, arguments.asInstanceOf[Arguments[Value]], location)

    override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) = {
      if (arguments.positional.nonEmpty) {
        throw EvalError("Cannot pass positional arguments to Object constructor", location)
      }

      BoundValue.Object(arguments.named, context.callScope, location)
    }
  }
))

object IntRootParams {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")
}

object IntRoot extends NativeObject(Map(
  "answer" -> new {} with PureMethod {
    override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
      PureValue.Int(42, location)
  }
))

object StringRootParams {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")
}

object StringRoot extends NativeObject(Map(
//  "assignableFrom" -> ScalaMethod(
//    MethodOptions(Seq(StringRootParams.Self, StringRootParams.Other)),
//    (_, args, l) => Value.Boolean(args.get(StringRootParams.Self) == args.get(StringRootParams.Other), l)
//  )
))

object CoreParams {
  val Self = Parameter(0, "self")

  val DefineMacroName = Parameter(1, "name")
  val DefineMacroLambda = Parameter(2, "lambda")

  val TypeCheckValue = Parameter(1, "value")
  val TypeCheckType = Parameter(2, "type")
}

object CoreTypes {
  val Type   = PureValue.Native(TypeRoot, None)
  val Nothing = PureValue.Native(NothingRoot, None)
  val Object = PureValue.Native(ObjectRoot, None)
  val Int    = PureValue.Native(IntRoot, None)
  val String = PureValue.Native(StringRoot, None)
  val List   = PureValue.Native(ListRoot, None)

  def Function(args: Seq[RealValue], returnType: RealValue) =
    PureValue.Native(FunctionType(args, returnType), None)
}

class Core extends NativeValue {
  val macros: mutable.TreeMap[String, RealValue] = mutable.TreeMap.empty

  val objectRootVar = new Variable(new VariableName("Object"), Some(CoreTypes.Object))

  val rootScope: Scope = Scope.newRoot(Seq(
    // TODO: Core should have a type
    new Variable(new VariableName("Core"),   Some(PureValue.Native(this, None))),
    new Variable(new VariableName("Type"),   Some(CoreTypes.Type)),
    objectRootVar,
    new Variable(new VariableName("Int"),    Some(CoreTypes.Int)),
    new Variable(new VariableName("String"), Some(CoreTypes.String)),
    new Variable(new VariableName("List"),   Some(CoreTypes.List))
  ))

  val staticRootScope = StaticScope.fromScope(rootScope)
  val unbinder = new Unbinder(this)

  private val methods = Map(
    "defineMacro" -> DefineMacroMethod(this),
    "typeCheck" -> TypeCheckMethod(this)
  )

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[ASTValue] = {
    macros.get(name) match {
      case Some(handler) =>
        val parserValue = PureValue.Native(ParserObject(parser), None)
        val valueResult = Core.nativeValueFor(handler).callOrThrowError(
          context,
          "call",
          Arguments(None, Seq(parserValue), Map.empty),
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

  override def method(
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = methods.get(name)

  def defineMacro(name: String, handler: RealValue): Unit =
    macros.addOne(name, handler)
}

private case class DefineMacroMethod(private val core: Core) extends NativeMethod {
  override val traits = Set(FunctionTrait.CompileTime)

  override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
    core.defineMacro(
      args.getString(CoreParams.DefineMacroName),
      // TODO: Support more value types as macro handlers (e.g. Structs or Natives)
      args.getFunction(CoreParams.DefineMacroLambda)
    )

    PureValue.Nothing(location)
  }

  override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
}

private case class TypeCheckMethod(private val core: Core) extends NativeMethod {
  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Partial)

  override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
    partialCall(context, args.asInstanceOf[Arguments[Value]], location)

  override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = {
    ???
//    val value = args.get(Parameter(1, "value"))
//    val typeValue = args.get(Parameter(2, "type")).realValue
//
//    if (value.realValue)
  }
}
