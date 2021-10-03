package photon.core

import photon.New.TypeObject
import photon.core.Conversions._
import photon.interpreter.{CallContext, EvalError}
import photon.lib.ObjectId
import photon.{Arguments, BoundValue, FunctionTrait, Location, MethodType, New, PureValue, RealValue, Scope, Value}

import scala.reflect.ClassTag

object Conversions {
  implicit class ValueAssert(value: Value) {
    def asBool: Boolean = value match {
      case PureValue.Boolean(value, _) => value
      case _ => throw EvalError(s"Invalid value type $value, expected Boolean", value.location)
    }

    def asInt: Int = value match {
      case PureValue.Int(value, _) => value
      case _ => throw EvalError(s"Invalid value type $value, expected Int", value.location)
    }

    def asFloat: Double = value match {
      case PureValue.Int(value, _) => value.toDouble
      case PureValue.Float(value, _) => value
      case _ => throw EvalError(s"Invalid value type $value, expected Int or Float", value.location)
    }

    def asString: String = value match {
      case PureValue.String(value, _) => value
      case _ => throw EvalError(s"Invalid value type $value, expected String", value.location)
    }

    def asBoundFunction: BoundValue.Function = value match {
      case fn: BoundValue.Function => fn
      case _ => throw EvalError(s"Invalid value type $value, expected Function", value.location)
    }

    def asObject: BoundValue.Object = value match {
      case obj: BoundValue.Object => obj
      case _ => throw EvalError(s"Invalid value type $value, expected Object", value.location)
    }

    def asNative[T <: NativeValue](implicit tag: ClassTag[T]) = value match {
      case PureValue.Native(native: T, _) => native
      case _ => throw EvalError(s"Invalid value type $value, expected some native", value.location)
    }

    //    def asNative[T <: NativeValue]: T = value match {
//      case PureValue.Native(native: NativeValue, _) if native.isInstanceOf[T] => native.asInstanceOf[T]
//      case _ => throw EvalError(s"Invalid value type $value, expected Native object", value.location)
//    }
  }

  implicit class ArgumentsAssertReal(arguments: Arguments[RealValue]) {
    def getBool(parameter: Parameter): Boolean = get(parameter).asBool
    def getInt(parameter: Parameter): Int = get(parameter).asInt
    def getFloat(parameter: Parameter): Double = get(parameter).asFloat
    def getString(parameter: Parameter): String = get(parameter).asString
    def getFunction(parameter: Parameter): BoundValue.Function = get(parameter).asBoundFunction
    def getObject(parameter: Parameter): BoundValue.Object = get(parameter).asObject
    def getNative[T <: NativeValue](parameter: Parameter)(implicit tag: ClassTag[T]): T = get(parameter).asNative[T]
    def getNativeSelf[T <: NativeValue](implicit tag: ClassTag[T]): T = get(Parameter(0, "self")).asNative[T]

    def get(parameter: Parameter): RealValue = {
      if (parameter.index == 0) {
        arguments.self.getOrElse { throw EvalError(s"Missing self argument", None) }
      } else if (parameter.index - 1 < arguments.positional.size) {
        arguments.positional(parameter.index - 1)
      } else {
        arguments.named.get(parameter.name) match {
          case Some(value) => value
          case None => throw EvalError(s"Missing argument ${parameter.name} (at index ${parameter.index}", None)
        }
      }
    }
  }

  implicit class ArgumentsAssertValue(arguments: Arguments[Value]) {
    def getBool(parameter: Parameter): Boolean = get(parameter).asBool
    def getInt(parameter: Parameter): Int = get(parameter).asInt
    def getDouble(parameter: Parameter): Double = get(parameter).asFloat
    def getString(parameter: Parameter): String = get(parameter).asString
    def getFunction(parameter: Parameter): BoundValue.Function = get(parameter).asBoundFunction
    def getObject(parameter: Parameter): BoundValue.Object = get(parameter).asObject

    def get(parameter: Parameter): Value = {
      if (parameter.index < arguments.positional.size) {
        arguments.positional(parameter.index)
      } else {
        arguments.named.get(parameter.name) match {
          case Some(value) => value
          case None => throw EvalError(s"Missing argument ${parameter.name} (at index ${parameter.index}", None)
        }
      }
    }
  }
}

trait NativeValue {
  val isFullyEvaluated: Boolean = true
  val typeObject: TypeObject

//  def method(
//    name: String,
//    location: Option[Location]
//  ): Option[NativeMethod]
//
//  def callOrThrowError(
//    context: CallContext,
//    name: String,
//    args: Arguments[RealValue],
//    location: Option[Location]
//  ): Value = {
//    method(name, location) match {
//      case Some(value) => value.call(context, args, location)
//      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
//    }
//  }
}

trait NativeMethod {
  val methodId = ObjectId()
  val traits: Set[FunctionTrait]
  val methodType: MethodType

  def call(
    context: CallContext,
    args: Arguments[RealValue],
    location: Option[Location]
  ): Value

  def partialCall(
    context: CallContext,
    args: Arguments[Value],
    location: Option[Location]
  ): Value
}

case class Parameter(index: Int, name: String)

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
