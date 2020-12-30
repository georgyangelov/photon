package photon

sealed abstract class Node
case class StaticValue(value: Value) extends Node

sealed abstract class Edge

class Context {
  def apply(value: Value): Unit = {
    value match {
      case Value.Unknown(location) =>
      case Value.Nothing(location) =>
      case Value.Boolean(value, location) =>
      case Value.Int(value, location, _) =>
      case Value.Float(value, location) =>
      case Value.String(value, location) =>
      case Value.Native(value, location) =>
      case Value.Struct(value, location) =>
      case Value.Lambda(value, location) =>
      case Value.Operation(operation, location) =>
    }
  }

  def unapply(value: Value): Unit = {

  }
}
