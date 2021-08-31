package photon.core

import photon.core.Conversions._
import photon.interpreter.{CallStackEntry, EvalError, Interpreter, RunMode}
import photon.lib.ObjectId
import photon.{Arguments, BoundValue, FunctionTrait, Location, PureValue, RealValue, Scope, Value}

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

    def asDouble: Double = value match {
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
  }

  implicit class ArgumentsAssertReal(arguments: Arguments[RealValue]) {
    def getBool(parameter: Parameter): Boolean = get(parameter).asBool
    def getInt(parameter: Parameter): Int = get(parameter).asInt
    def getDouble(parameter: Parameter): Double = get(parameter).asDouble
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

  implicit class ArgumentsAssertValue(arguments: Arguments[Value]) {
    def getBool(parameter: Parameter): Boolean = get(parameter).asBool
    def getInt(parameter: Parameter): Int = get(parameter).asInt
    def getDouble(parameter: Parameter): Double = get(parameter).asDouble
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

case class CallContext(
  interpreter: Interpreter,
  runMode: RunMode,
  callStack: Seq[CallStackEntry],
  callScope: Scope
)

trait NativeValue {
  val isFullyEvaluated: Boolean = true

  def method(
    name: String,
    location: Option[Location]
  ): Option[NativeMethod]

  def callOrThrowError(
    context: CallContext,
    name: String,
    args: Arguments[RealValue],
    location: Option[Location]
  ): Value = {
    method(name, location) match {
      case Some(value) => value.call(context, args, location)
      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
    }
  }
}

trait NativeMethod {
  val methodId = ObjectId()
  val traits: Set[FunctionTrait]

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

trait PureMethod extends NativeMethod {
  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)

  override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
    throw new NotImplementedError("partialCall not implemented")
}

trait PartialMethod extends NativeMethod {
  override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Partial, FunctionTrait.Pure)
}

case class Parameter(index: Int, name: String)

case class LambdaMetadata(withSideEffects: Boolean = false)

case class MethodOptions(
  parameters: Seq[Parameter],
  traits: Set[FunctionTrait] = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)
)

@deprecated
case class ScalaMethod(
  options: MethodOptions,
  callHandler: ScalaMethod#CallHandler,
  override val methodId: ObjectId = ObjectId()
) extends NativeMethod {
  type CallHandler = (CallContext, Arguments[RealValue], Option[Location]) => Value

  override val traits: Set[FunctionTrait] = options.traits

  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
    callHandler.apply(context, arguments, location)

  override def partialCall(context: CallContext, arguments: Arguments[Value], location: Option[Location]) = ???
}

class NativeObject(methods: Map[String, NativeMethod]) extends NativeValue {
  override def method(
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = methods.get(name)
}
