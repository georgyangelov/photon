//package photon.core
//
//import photon.{AnyType, ArgumentType, Arguments, BoundValue, Location, MethodType, New, PureValue, RealValue, TypeType}
//import photon.core.Conversions._
//import photon.core.IntParams.{FirstParam, SecondParam}
//import photon.interpreter.CallContext
//
//object LazyTypeType extends New.TypeObject {
//  override val typeObject = TypeType
//  override val instanceMethods = Map(
//    // Lazy.new
//    "new" -> new New.CompileTimeOnlyMethod {
//      override def methodType(argTypes: Arguments[New.TypeObject]) = {
//        val argumentFunctionTypeObject = argTypes.positional.head
//
//        MethodType(
//          name = "new",
//          arguments = Seq(ArgumentType("value", argumentFunctionTypeObject)),
//          // TODO: Unwrap the type of the function
//          returns = AnyType
//        )
//      }
//
//      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//        PureValue.Native(LazyInstance(args.getFunction(Parameter(0, "resolve"))), location)
//    }
//  )
//}
//
//// Lazy
//case class LazyType(innerTypeObject: New.TypeObject) extends New.TypeObject {
//  override val instanceMethods = _
//  override val typeObject = innerTypeObject.typeObject
//}
//
//case class LazyInstance(function: BoundValue.Function) extends NativeValue {
//  override val typeObject = LazyType
//}