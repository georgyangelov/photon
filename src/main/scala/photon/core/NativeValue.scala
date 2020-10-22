package photon.core

import photon.{EvalError, Interpreter, Lambda, Location, Scope, Value}

object NativeValue {
  implicit class ValueAssert(value: Value) {
    def asBool: Boolean = value match {
      case Value.Boolean(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asInt: Int = value match {
      case Value.Int(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asDouble: Double = value match {
      case Value.Int(value, _) => value.toDouble
      case Value.Float(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError("Invalid value type ...", None)
    }

    def asString: String = value match {
      case Value.String(value, _) => value
      // TODO: Message and location
      case _ => throw EvalError(s"Invalid value type $value, expected String", None)
    }

    def asLambda: Lambda = value match {
      case Value.Lambda(lambda, _) => lambda
      case _ => throw EvalError("Invalid value type ...", None)
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

case class CallContext(interpreter: Interpreter, shouldTryToPartiallyEvaluate: Boolean, isInPartialEvaluation: Boolean)

trait NativeValue {
  def method(
    context: CallContext,
    name: String,
    location: Option[Location]
  ): Option[NativeMethod]

  def callOrThrowError(
    context: CallContext,
    name: String,
    args: Seq[Value],
    location: Option[Location]
  ): Value = {
    method(context, name, location) match {
      case Some(value) => value.call(context, args, location)
      case None => throw EvalError(s"Cannot call method $name on ${this.toString}", location)
    }
  }
}

trait NativeMethod {
  val withSideEffects: Boolean

  def call(
    context: CallContext,
    arguments: Seq[Value],
    location: Option[Location]
  ): Value
}

case class LambdaMetadata(withSideEffects: Boolean = false)
case class ScalaMethod(
  handler: ScalaMethod#MethodHandler,
  override val withSideEffects: Boolean = false
) extends NativeMethod {
  type MethodHandler = (CallContext, Seq[Value], Option[Location]) => Value

  override def call(context: CallContext, arguments: Seq[Value], location: Option[Location]): Value = {
    handler.apply(context, arguments, location)
  }
}

class NativeObject(methods: Map[String, NativeMethod]) extends NativeValue {
  override def method(
    context: CallContext,
    name: String,
    location: Option[Location]
  ): Option[NativeMethod] = {
    methods.get(name) match {
      case Some(method) => Some(method)
      case None => None
    }
  }
}