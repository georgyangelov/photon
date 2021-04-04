package photon.core

import photon.core.NativeValue.ValueAssert
import photon.{Arguments, EvalError, Interpreter, Lambda, LambdaTrait, Location, RunMode, Struct, Value}

object NativeValue {
  implicit class ValueAssert(value: Value) {
    def asBool: Boolean = value match {
      case Value.Boolean(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asInt: Int = value match {
      case Value.Int(value, _, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asDouble: Double = value match {
      case Value.Int(value, _, _) => value.toDouble
      case Value.Float(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asString: String = value match {
      case Value.String(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError(s"Invalid value type $value, expected String", value.location)
    }

    def asLambda: Lambda = value match {
      case Value.Lambda(lambda, _) => lambda
      case _ => throw EvalError(s"Expected lambda, got $value", value.location)
    }

    def asStruct: Struct = value match {
      case Value.Struct(struct, _) => struct
      case _ => throw EvalError(s"Invalid value type $value, expected Struct", value.location)
    }
  }

  implicit class ArgListAssert(argumentList: Seq[Value]) {
    def getBool(index: Int): Boolean = get(index).asBool
    def getInt(index: Int): Int = get(index).asInt
    def getDouble(index: Int): Double = get(index).asDouble
    def getString(index: Int): String = get(index).asString
    def getLambda(index: Int): Lambda = get(index).asLambda

    def get(index: Int): Value = {
      if (index >= argumentList.size) {
        // TODO: Message and location
        throw EvalError("No argument ...", None)
      }

      argumentList(index)
    }
  }
}

case class CallContext(interpreter: Interpreter, runMode: RunMode)

trait NativeValue {
  // TODO: Implement
  // def typeStruct: Either[NativeValue, Struct]

  def method(
    context: CallContext,
    name: String,
    location: Option[Location]
  ): Option[NativeMethod]

  def callOrThrowError(
    context: CallContext,
    name: String,
    args: Arguments,
    location: Option[Location]
  ): Value = {
    method(context, name, location) match {
      case Some(value) => value.call(context, args, location)
      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
    }
  }
}

trait NativeMethod {
  val traits: Set[LambdaTrait]

  def call(
    context: CallContext,
    arguments: Arguments,
    location: Option[Location]
  ): Value
}

case class Parameter(index: Int, name: String)

case class AppliedParameters(parameters: Seq[Parameter], arguments: Arguments) {
  def getBool(parameter: Parameter): Boolean = get(parameter).asBool
  def getInt(parameter: Parameter): Int = get(parameter).asInt
  def getDouble(parameter: Parameter): Double = get(parameter).asDouble
  def getString(parameter: Parameter): String = get(parameter).asString
  def getLambda(parameter: Parameter): Lambda = get(parameter).asLambda

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

case class LambdaMetadata(withSideEffects: Boolean = false)

case class MethodOptions(
  parameters: Seq[Parameter],
  traits: Set[LambdaTrait] = Set(LambdaTrait.Partial, LambdaTrait.CompileTime, LambdaTrait.Runtime, LambdaTrait.Pure)
)

case class ScalaMethod(
  options: MethodOptions,
  handler: ScalaMethod#MethodHandler
) extends NativeMethod {
  type MethodHandler = (CallContext, AppliedParameters, Option[Location]) => Value

  override val traits: Set[LambdaTrait] = options.traits

  override def call(context: CallContext, arguments: Arguments, location: Option[Location]): Value = {
    handler.apply(
      context,
      AppliedParameters(options.parameters, arguments),
      location
    )
  }
}

case class ScalaVarargMethod(
  handler: ScalaVarargMethod#MethodHandler,
  traits: Set[LambdaTrait] = Set(LambdaTrait.Partial, LambdaTrait.CompileTime, LambdaTrait.Runtime, LambdaTrait.Pure)
) extends NativeMethod {
  type MethodHandler = (CallContext, Arguments, Option[Location]) => Value

  override def call(context: CallContext, arguments: Arguments, location: Option[Location]): Value = {
    handler.apply(context, arguments, location)
  }
}

class NativeObject(methods: Map[String, NativeMethod]) extends NativeValue {
  override def method(
    context: CallContext,
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = methods.get(name)
}
