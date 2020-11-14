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

object StructObject extends NativeObject(Map(
  "to_bool" -> ScalaMethod({ (_, _, l) => Value.Boolean(true, l) })
)) {
  override def method(context: CallContext, name: String, target: Value, location: Option[Location]): Option[NativeMethod] = {
    val struct = target.asStruct

    if (struct.props.contains(name)) {
      return Some(StructGetter(name))
    }

    val method = struct.props.get("$method")
    method match {
      case Some(Value.Lambda(lambda, _)) => invokeMethodHandler(context, target, lambda, name, location)
      case Some(_) => throw EvalError("$method must be a lambda", location)
      case None => ()
    }

    super.method(context, name, target, location)
  }

  private def invokeMethodHandler(context: CallContext, structValue: Value, lambda: Lambda, name: String, location: Option[Location]): Option[NativeMethod] = {
    // TODO: Verify this is side-effect-free
    val methodHandlerResult = Core.nativeValueFor(lambda).callOrThrowError(context, "call", Seq(structValue, Value.String(name, None)), location)

    methodHandlerResult match {
      case Value.Nothing(_) => None
      case Value.Lambda(lambda, _) => Some(Core.nativeValueFor(lambda).method(context, "call", structValue, location).get)
      case _ => throw EvalError(s"$$method must return either $$nothing or a lambda, got $methodHandlerResult", location)
    }
  }
}
