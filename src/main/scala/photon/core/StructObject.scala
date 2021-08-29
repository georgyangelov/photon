//package photon.core
//
//import photon.{Arguments, EvalError, FunctionTrait, Location, ObjectId, Struct, Value}
//import photon.core.NativeValue._
//
////case class StructGetter(propertyName: String) extends NativeMethod {
////  // FIXME: This is wrong, it's different for every call
////  override val methodId = ObjectId()
////
////  // FIXME: This should assume the traits from the lambda if the property is a lambda
////  override val traits = Set(LambdaTrait.CompileTime, LambdaTrait.Runtime)
////
////  override def call(context: CallContext, arguments: Arguments, location: Option[Location]): Value = {
////    val struct = arguments.positional.head.asStruct
////    val value = struct.props.get(propertyName)
////
////    value match {
////      case Some(Value.Lambda(lambda, _)) =>
////        Core.nativeValueFor(lambda).callOrThrowError(context, "call", arguments, location)
////      case Some(value) => value
////      case None => throw EvalError(s"StructGetter cannot return property ${propertyName}", location)
////    }
////  }
////}
//
//object Getter {
//  val globalGetterMethodId = ObjectId()
//}
//
//case class StructGetter(propertyName: String) extends NativeMethod {
//  // This being the same doesn't matter because getters will never recursively call each other
//  override val methodId = Getter.globalGetterMethodId
//
//  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime)
//
//  override def call(context: CallContext, arguments: Arguments, location: Option[Location]) = {
//    val struct = arguments.positional.head.asStruct
//    val value = struct.props.get(propertyName)
//
//    value match {
//      case Some(value) => value
//      case None => throw EvalError(s"StructGetter cannot return property ${propertyName}", location)
//    }
//  }
//}
//
//case class StructObject(struct: Struct) extends NativeObject(Map(
//  "to_bool" -> ScalaMethod(
//    MethodOptions(Seq.empty),
//    { (_, _, l) => Value.Boolean(true, l) }
//  )
//)) {
//  override def method(name: String, location: Option[Location]): Option[NativeMethod] = {
//    if (struct.props.contains(name)) {
//      val value = struct.props.get(name)
//
//      value match {
//        case Some(Value.BoundFunction(lambda, _)) => Core.nativeValueFor(lambda).method("call", location)
//        case Some(_) => Some(StructGetter(name))
//        case None => super.method(name, location)
//      }
//    } else {
//      super.method(name, location)
//    }
//
////    val method = struct.props.get("$method")
////    method match {
////      case Some(Value.Lambda(lambda, _)) => return invokeMethodHandler(context, lambda, name, location)
////      case Some(_) => throw EvalError("$method must be a lambda", location)
////      case None => ()
////    }
//  }
//
////  private def invokeMethodHandler(context: CallContext, lambda: Lambda, name: String, callLocation: Option[Location]): Option[NativeMethod] = {
////    val structValue = Value.Struct(struct, structLocation)
////
////    val arguments = Arguments(Seq(structValue, Value.String(name, None)), Map.empty)
////
////    // TODO: Verify this is side-effect-free
////    val methodHandlerResult = Core.nativeValueFor(lambda).callOrThrowError(context, "call", arguments, callLocation)
////
////    methodHandlerResult match {
////      case Value.Nothing(_) => None
////      case Value.Lambda(lambda, _) => Some(Core.nativeValueFor(lambda).method(context, "call", callLocation).get)
////      case _ => throw EvalError(s"$$method must return either $$nothing or a lambda, got $methodHandlerResult", callLocation)
////    }
////  }
//}
