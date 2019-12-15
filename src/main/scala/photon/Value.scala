package photon

import scala.collection.immutable.ListMap

sealed abstract class Value {
  def location: Option[Location]

  def inspect: String = {
    this match {
      case Value.Unknown(_) => "$?"
      case Value.Nothing(_) => "$nothing"

      case Value.Boolean(value, _) => value.toString
      case Value.Int(value, _) => value.toString
      case Value.Float(value, _) => value.toString
      case Value.String(value, _) =>
        val escapedString = value.replaceAll("([\"\\\\])", "\\\\$1").replaceAllLiterally("\n", "\\n")

        '"' + escapedString + '"'

      case Value.Struct(struct, _) =>
        val values = struct.props.iterator.map { case (key, value) => s"$key: ${value.inspect}" }

        s"$${${values.mkString(", ")}}"

      case Value.Lambda(lambda, _) =>
        val body = lambda.body.inspect
        val params = lambda.params.map(name => s"(param $name)").mkString(" ")

        s"(lambda [$params] $body)"

      case Value.Operation(operation, _) => operation.inspect
    }
  }
}

object Value {
  case class Unknown(location: Option[Location]) extends Value
  case class Nothing(location: Option[Location]) extends Value

  case class Boolean(value: scala.Boolean, location: Option[Location]) extends Value
  case class Int(value: scala.Int, location: Option[Location]) extends Value
  case class Float(value: scala.Double, location: Option[Location]) extends Value
  case class String(value: java.lang.String, location: Option[Location]) extends Value

  case class Struct(value: photon.Struct, location: Option[Location]) extends Value
  case class Lambda(value: photon.Lambda, location: Option[Location]) extends Value

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value
}

case class Struct(props: Map[String, Value])
case class Lambda(params: Seq[String], body: Operation.Block)

sealed class Operation {
  def inspect: String = {
    this match {
      case Operation.Assignment(name, value) => s"($$assign $name ${value.inspect})"
      case Operation.Block(values) =>
        s"{ ${values.map(_.inspect).mkString(" ")} }"

      case Operation.Call(target, name, arguments, _) =>
        if (arguments.isEmpty) {
          s"($name ${target.inspect})"
        } else {
          s"($name ${target.inspect} ${arguments.map(_.inspect).mkString(" ")})"
        }

      case Operation.NameReference(name) => name
    }
  }
}

object Operation {
  case class Assignment(name: String, value: Value) extends Operation
  case class Block(values: Seq[Value]) extends Operation
  case class Call(target: Value, name: String, arguments: Seq[Value], mayBeVarCall: Boolean) extends Operation
  case class NameReference(name: String) extends Operation
}
