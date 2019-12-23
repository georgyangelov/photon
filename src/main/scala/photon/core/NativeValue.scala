package photon.core

import photon.{EvalError, Interpreter, Lambda, Location, Scope, Value}

object NativeValue {
  implicit class ValueAssert(value: Value) {
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
    def getInt(index: Int): Int = get(index).asInt
    def getDouble(index: Int): Double = get(index).asDouble
    def getString(index: Int): String = get(index).asString
    def getLambda(index: Int): Lambda = get(index).asLambda

    private def get(index: Int): Value = {
      if (index >= argumentList.size) {
        // TODO: Message and location
        throw EvalError("No argument ...", None)
      }

      argumentList(index)
    }
  }
}

case class CallContext(interpreter: Interpreter)

trait NativeValue {
  def call(
    context: CallContext,
    name: String,
    arguments: Seq[Value],
    location: Option[Location]
  ): Value = {
    throw EvalError(s"Cannot call method $name on ${this.toString}", location)
  }
}

class NativeObject(methods: Map[String, NativeObject#MethodHandler]) extends NativeValue {
  type MethodHandler = (CallContext, Seq[Value], Option[Location]) => Value;

  override def call(
    context: CallContext,
    name: String,
    arguments: Seq[Value],
    location: Option[Location]
  ): Value = {
    methods.get(name) match {
      case Some(handler) => handler.apply(context, arguments, location)
      case None => super.call(context, name, arguments, location)
    }
  }
}
