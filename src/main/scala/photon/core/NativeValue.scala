package photon.core

import photon.core.NativeValue.ValueAssert
import photon.interpreter.{CallStackEntry, EvalError, Interpreter, RunMode}
import photon.lib.ObjectId
import photon.{Arguments, BoundValue, FunctionTrait, Location, PureValue, RealValue, Scope, Value}

object NativeValue {
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

//    TODO
//    def asStruct: Struct = value match {
//      case Value.Struct(struct, _) => struct
//      case _ => throw EvalError(s"Invalid value type $value, expected Struct", value.location)
//    }
  }

  implicit class ArgListAssert(argumentList: Seq[Value]) {
    def getBool(index: Int): Boolean = get(index).asBool
    def getInt(index: Int): Int = get(index).asInt
    def getDouble(index: Int): Double = get(index).asDouble
    def getString(index: Int): String = get(index).asString
    def getFunction(index: Int): BoundValue.Function = get(index).asBoundFunction

    def get(index: Int): Value = {
      if (index >= argumentList.size) {
        // TODO: Message and location
        throw EvalError("No argument ...", None)
      }

      argumentList(index)
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
  // TODO: Implement
  // def typeStruct: Either[NativeValue, Struct]

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
  val methodId: ObjectId
  val traits: Set[FunctionTrait]

  def call(
    context: CallContext,
    arguments: Arguments[RealValue],
    location: Option[Location]
  ): Value
}

case class Parameter(index: Int, name: String)

case class AppliedParameters(parameters: Seq[Parameter], arguments: Arguments[RealValue]) {
  def getBool(parameter: Parameter): Boolean = get(parameter).asBool
  def getInt(parameter: Parameter): Int = get(parameter).asInt
  def getDouble(parameter: Parameter): Double = get(parameter).asDouble
  def getString(parameter: Parameter): String = get(parameter).asString
  def getFunction(parameter: Parameter): BoundValue.Function = get(parameter).asBoundFunction

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
  traits: Set[FunctionTrait] = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure)
)

case class ScalaMethod(
  options: MethodOptions,
  handler: ScalaMethod#MethodHandler,
  methodId: ObjectId = ObjectId()
) extends NativeMethod {
  type MethodHandler = (CallContext, AppliedParameters, Option[Location]) => Value

  override val traits: Set[FunctionTrait] = options.traits

  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
    handler.apply(
      context,
      AppliedParameters(options.parameters, arguments),
      location
    )
}

case class ScalaVarargMethod(
  handler: ScalaVarargMethod#MethodHandler,
  traits: Set[FunctionTrait] = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime, FunctionTrait.Pure),
  methodId: ObjectId = ObjectId()
) extends NativeMethod {
  type MethodHandler = (CallContext, Arguments[RealValue], Option[Location]) => Value

  override def call(context: CallContext, arguments: Arguments[RealValue], location: Option[Location]) =
    handler.apply(context, arguments, location)
}

class NativeObject(methods: Map[String, NativeMethod]) extends NativeValue {
  override def method(
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = methods.get(name)
}
