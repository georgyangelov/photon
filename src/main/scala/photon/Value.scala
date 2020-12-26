package photon

import java.util.concurrent.atomic.AtomicLong

import photon.core.NativeValue

case class ObjectId(id: Long) extends AnyVal

object ObjectId {
  val idCounter = new AtomicLong(1)

  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
}

trait WithObjectId {
  val id: ObjectId = ObjectId()
}

sealed abstract class Value extends WithObjectId {
  def location: Option[Location]

  override def toString: String = Unparser.unparse(this)

  def inspect: String = {
    this match {
      case Value.Unknown(_) => "$?"
      case Value.Nothing(_) => "$nothing"

      case Value.Boolean(value, _) => value.toString
      case Value.Int(value, _) => value.toString
      case Value.Float(value, _) => value.toString
      case Value.String(value, _) =>
        val escapedString = value
          .replaceAll("([\"\\\\])", "\\\\$1")
          .replaceAllLiterally("\n", "\\n")

        '"' + escapedString + '"'

      case Value.Native(native, _) => s"<${native.toString}>"

      case Value.Struct(struct, _) =>
        val values = struct.props.iterator.map { case (key, value) => s"$key = ${value.inspect}" }

        s"$${${values.mkString(", ")}}"

      case Value.Lambda(lambda, _) =>
        val body = lambda.body.inspect
        val params = lambda.params.map(name => s"(param $name)").mkString(" ")

        s"(lambda [$params] $body)"

      case Value.Operation(operation, _) => operation.inspect
    }
  }

  def isOperation: Boolean = {
    this match {
      case Value.Operation(_, _) => true
      case _ => false
    }
  }

  def isUnknown: Boolean = {
    this match {
      case Value.Unknown(_) => true
      case _ => false
    }
  }

  // TODO: If the value is a Lambda which closes over dynamic values, then it is dynamic as well
  //       Maybe the Analysis functionality should say what value is static and what is dynamic...
  def isStatic: Boolean = !isUnknown && !isOperation
  def isDynamic: Boolean = !isStatic
}

object Value {
  case class Unknown(location: Option[Location]) extends Value
  case class Nothing(location: Option[Location]) extends Value

  case class Boolean(value: scala.Boolean, location: Option[Location]) extends Value
  case class Int(value: scala.Int, location: Option[Location]) extends Value
  case class Float(value: scala.Double, location: Option[Location]) extends Value
  case class String(value: java.lang.String, location: Option[Location]) extends Value

  case class Native(native: NativeValue, location: Option[Location]) extends Value
  case class Struct(value: photon.Struct, location: Option[Location]) extends Value
  case class Lambda(value: photon.Lambda, location: Option[Location]) extends Value

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value
}

case class Scope(parent: Option[Scope], values: Map[String, Value]) {
  override def toString: String = {
    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: String): Option[Value] = {
    values.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}

case class Struct(props: Map[String, Value]) {
  override def toString: String = Unparser.unparse(this)
}

case class Lambda(params: Seq[String], scope: Option[Scope], body: Operation.Block) {
  override def toString: String = Unparser.unparse(this)
}

sealed abstract class Operation {
  override def toString: String = Unparser.unparse(this)

  def inspect: String = {
    this match {
      case Operation.Assignment(name, value) => s"($$assign $name ${value.inspect})"
      case Operation.Block(values) =>
        s"{ ${values.map(_.inspect).mkString(" ")} }"

      case Operation.Call(target, name, arguments, _) =>
        val positionalArguments = arguments.positional.map(_.inspect)
        val namedArguments = arguments.named.map { case (name, value) => s"(param ${name} ${value.inspect})" }
        val argumentStrings = positionalArguments ++ namedArguments

        if (argumentStrings.isEmpty) {
          s"($name ${target.inspect})"
        } else {
          s"($name ${target.inspect} ${argumentStrings.mkString(" ")})"
        }

      case Operation.NameReference(name) => name
    }
  }
}

object Operation {
  case class Assignment(name: String, value: Value) extends Operation
  case class Block(values: Seq[Value]) extends Operation
  case class Call(target: Value, name: String, arguments: Arguments, mayBeVarCall: Boolean) extends Operation
  case class NameReference(name: String) extends Operation
}

case class Arguments(positional: Seq[Value], named: Map[String, Value])

object Arguments {
  val empty: Arguments = Arguments(Seq.empty, Map.empty)
}