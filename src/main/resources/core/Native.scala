package photon.core

import photon.EValue
import photon.New.TypeObject
import photon.interpreter.{CallContext, EvalError}
import photon.lib.ObjectId

//trait NativeValue {
//  val isFullyEvaluated: Boolean = true
//  val typeObject: TypeObject
//
////  def method(
////    name: String,
////    location: Option[Location]
////  ): Option[NativeMethod]
////
////  def callOrThrowError(
////    context: CallContext,
////    name: String,
////    args: Arguments[RealValue],
////    location: Option[Location]
////  ): Value = {
////    method(name, location) match {
////      case Some(value) => value.call(context, args, location)
////      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
////    }
////  }
//}

//trait Method {
//  val methodId = ObjectId()
//  val traits: Set[FunctionTrait]
//
//  def methodType(argTypes: Arguments[New.TypeObject]): MethodType
//
//  def call(
//    context: CallContext,
//    args: Arguments[RealValue],
//    location: Option[Location]
//  ): Value
//}

//case class Parameter(index: Int, name: String)

//case class GetterMethod(
//  private val value: RealValue,
//  traits: Set[FunctionTrait]
//) extends NativeMethod {
//  override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//    value
//
//  override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
//    value
//}
