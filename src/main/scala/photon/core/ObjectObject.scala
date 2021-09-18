package photon.core
import photon.{Arguments, BoundValue, FunctionTrait, Location, RealValue, Value}
import photon.core.Conversions._

case class ObjectObject(obj: BoundValue.Object) extends NativeValue {
  override val typeValue = CoreTypes.Type

  override def method(name: String, location: Option[Location]): Option[NativeMethod] = {
    val value = obj.values.get(name)

    value
      .map(_.realValue)
      .flatMap {
        case Some(boundFn: BoundValue.Function) => Core.nativeValueFor(boundFn).method("call", location)
        case Some(value) => Some(Getter(value))
        case None => None
      }
      .orElse { instanceMethods.flatMap { value => Core.nativeValueFor(value).method(name, location) } }
      .orElse {
        if (value.isDefined) Some(CurrentlyUnknown)
        else None
      }
  }

  // TODO: This can be Native
  private def typeObject: Option[BoundValue.Object] =
    obj.typeValue //.values.get("$type")
      .flatMap(_.realValue)
      .map(_.asObject)

  // TODO: This can be Native
  private def instanceMethods: Option[BoundValue.Object] =
    typeObject
      .flatMap(_.values.get("$instanceMethods"))
      .flatMap(_.realValue)
      .map(_.asObject)
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
