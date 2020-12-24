package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Lambda, Location, Scope, Struct, Value}
import photon.core.NativeValue._

case class StructGetter(propertyName: String) extends NativeMethod {
  override val withSideEffects = false

  override def call(context: CallContext, arguments: Seq[Value], location: Option[Location]): Value = {
    val struct = arguments.head.asStruct
    val value = struct.props.get(propertyName)

    value match {
      case Some(value) => value
      case None => throw EvalError(s"StructGetter cannot return property ${propertyName}", location)
    }
  }
}

case class StructObject(struct: Struct, structLocation: Option[Location]) extends NativeObject(Map(
  "to_bool" -> ScalaMethod({ (_, _, l) => Value.Boolean(true, l) })
)) {
  override def method(context: CallContext, name: String, location: Option[Location]): Option[NativeMethod] = {
    if (struct.props.contains(name)) {
      return Some(StructGetter(name))
    }

    val method = struct.props.get("$method")
    method match {
      case Some(Value.Lambda(lambda, _)) => return invokeMethodHandler(context, lambda, name, location)
      case Some(_) => throw EvalError("$method must be a lambda", location)
      case None => ()
    }

    super.method(context, name, location)
  }

  private def invokeMethodHandler(context: CallContext, lambda: Lambda, name: String, callLocation: Option[Location]): Option[NativeMethod] = {
    val structValue = Value.Struct(struct, structLocation)

    // TODO: Verify this is side-effect-free
    val methodHandlerResult = Core.nativeValueFor(lambda).callOrThrowError(context, "call", Seq(structValue, Value.String(name, None)), callLocation)

    methodHandlerResult match {
      case Value.Nothing(_) => None
      case Value.Lambda(lambda, _) => Some(Core.nativeValueFor(lambda).method(context, "call", callLocation).get)
      case _ => throw EvalError(s"$$method must return either $$nothing or a lambda, got $methodHandlerResult", callLocation)
    }
  }
}