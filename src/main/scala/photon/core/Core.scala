package photon.core

import photon.frontend.{ASTValue, Parser, StaticScope, ValueToAST}
import photon.{AnyType, ArgumentType, Arguments, FunctionTrait, Location, MethodType, New, PureValue, RealValue, Scope, TypeType, Variable, VariableName}
import photon.interpreter.{CallContext, Unbinder}

import scala.collection.mutable
import photon.core.Conversions._

object CoreParams {
  val Self = Parameter(0, "self")

  val DefineMacroName = Parameter(1, "name")
  val DefineMacroLambda = Parameter(2, "lambda")

  val TypeCheckValue = Parameter(1, "value")
  val TypeCheckType = Parameter(2, "type")
}

object CoreType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    "defineMacro" -> new New.CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "defineMacro",
        arguments = Seq(
          ArgumentType("name", StringType),
          ArgumentType("handler", FunctionType(
            Set(FunctionTrait.Partial),
            Seq(ArgumentType("parser", ParserType)),
            AnyType
          )),
        ),
        // TODO
        returns = AnyType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val self = args.getNativeSelf[Core]

        self.defineMacro(
          args.getString(CoreParams.DefineMacroName),
          // TODO: Support more value types as macro handlers (e.g. Structs or Natives)
          args.getFunction(CoreParams.DefineMacroLambda)
        )

        PureValue.Nothing(location)
      }
    },

    //    "typeCheck" -> TypeCheckMethod(this)
  )
}

class Core extends {
  override val typeObject = CoreType
  override val instanceMethods: Map[String, NativeMethod] = Map.empty
} with New.TypeObject {
  val macros: mutable.TreeMap[String, RealValue] = mutable.TreeMap.empty

  val objectRootVar = new Variable(new VariableName("Object"), Some(ObjectType.toValue))

  val rootScope: Scope = Scope.newRoot(Seq(
    // TODO: Core should have a type
    new Variable(new VariableName("Core"),     Some(this.toValue)),
    new Variable(new VariableName("Type"),     Some(TypeType.toValue)),
    objectRootVar,
    new Variable(new VariableName("Int"),      Some(IntType.toValue)),
    new Variable(new VariableName("Float"),    Some(FloatType.toValue)),
    new Variable(new VariableName("String"),   Some(StringType.toValue)),
    new Variable(new VariableName("List"),     Some(ListType.toValue)),
    new Variable(new VariableName("Function"), Some(FunctionTypeType.toValue))
  ))

  val staticRootScope = StaticScope.fromScope(rootScope)
  val unbinder = new Unbinder(this)

  def macroHandler(context: CallContext, name: String, parser: Parser): Option[ASTValue] = {
    macros.get(name) match {
      case Some(handler) =>
        val parserValue = PureValue.Native(ParserObject(parser), None)

        val valueResult = context.callOrThrowError(
          handler,
          "call",
          Arguments(Some(handler), Seq(parserValue), Map.empty),
          // TODO: better location here
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
