package photon.core
import photon.{AnyType, ArgumentType, Arguments, BoundValue, FunctionTrait, Location, MethodType, New, RealValue, TypeType, Value}
import photon.core.Conversions._
import photon.interpreter.{CallContext, EvalError}

object ObjectTypeType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    "new" -> new NativeMethod {
      final override val traits = Set(
        FunctionTrait.CompileTime,
        FunctionTrait.Runtime,
        FunctionTrait.Partial,
        FunctionTrait.Pure
      )

      override val methodType = MethodType(
        name = "new",
        argumentTypes = Seq(
          // TODO: Make this optional
          ArgumentType("type", TypeType)
        ),
        // TODO: Make this not be any
        returnType = AnyType
      )

      override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
        partialCall(context, arguments.asInstanceOf[Arguments[Value]], location)

      override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) = {
        if (arguments.positional.nonEmpty) {
          throw EvalError("Cannot pass positional arguments to Object constructor", location)
        }

        BoundValue.Object(arguments.named, context.callScope, ???, location)
      }
    }
  )
}

// TODO: Rename to Struct or Dynamic?
object ObjectType extends New.TypeObject {
  override val typeObject = ObjectTypeType

  override val instanceMethods = Map.empty
}









//case class ObjectObject(obj: BoundValue.Object) extends NativeValue {
//  override val typeObject = CoreTypes.Type
//
//  override def method(name: String, location: Option[Location]): Option[NativeMethod] = {
//    val value = obj.values.get(name)
//
//    value
//      .map(_.realValue)
//      .flatMap {
//        case Some(boundFn: BoundValue.Function) => Core.nativeValueFor(boundFn).method("call", location)
//        case Some(value) => Some(Getter(value))
//        case None => None
//      }
//      .orElse { instanceMethods.flatMap { value => Core.nativeValueFor(value).method(name, location) } }
//      .orElse {
//        if (value.isDefined) Some(CurrentlyUnknown)
//        else None
//      }
//  }
//
//  // TODO: This can be Native
//  private def typeObject: Option[BoundValue.Object] =
//    obj.typeObject //.values.get("$type")
//      .flatMap(_.realValue)
//      .map(_.asObject)
//
//  // TODO: This can be Native
//  private def instanceMethods: Option[BoundValue.Object] =
//    typeObject
//      .flatMap(_.values.get("$instanceMethods"))
//      .flatMap(_.realValue)
//      .map(_.asObject)
//}

//private object CurrentlyUnknown extends NativeMethod {
//  override val traits = Set.empty
//
//  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
//    throw new NotImplementedError()
//
//  override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) =
//    throw new NotImplementedError()
//}

//private case class Getter(value: Value) extends NativeMethod {
//  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)
//
//  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) = value
//
//  override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) =
//    throw new NotImplementedError("Cannot partially call a getter")
//}
