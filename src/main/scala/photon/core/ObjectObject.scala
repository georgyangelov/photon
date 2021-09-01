package photon.core
import photon.{Arguments, BoundValue, FunctionTrait, Location, RealValue, Value}

case class ObjectObject(obj: BoundValue.Object) extends NativeValue {
  override def method(name: String, location: Option[Location]): Option[NativeMethod] =
    obj.values.get(name).map(_.realValue).flatMap {
      case Some(boundFn: BoundValue.Function) => Core.nativeValueFor(boundFn).method("call", location)
      case Some(value) => Some(Getter(value))
      case None => Some(CurrentlyUnknown)
    }
}

private object CurrentlyUnknown extends NativeMethod {
  override val traits = Set.empty

  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
    throw new NotImplementedError()

  override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) =
    throw new NotImplementedError()
}

private case class Getter(value: Value) extends NativeMethod {
  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)

  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) = value

  override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) =
    throw new NotImplementedError("Cannot partially call a getter")
}