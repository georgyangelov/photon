package photon

import photon.core.NativeValue

import java.util.concurrent.atomic.AtomicLong
import scala.collection.Map

//case class ObjectId(id: Long) extends AnyVal
//
//object ObjectId {
//  val idCounter = new AtomicLong(1)
//
//  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
//}
//
//trait WithObjectId {
//  val id: ObjectId = ObjectId()
//}

sealed abstract class Value {
  def location: Option[Location]
  def typeObject: Option[TypeObject] = None

  override def toString: String = Unparser.unparse(this)

  def inspectAST: String = {
    this match {
      case Value.Unknown(_) => "$?"
      case Value.Nothing(_) => "$nothing"

      case Value.Boolean(value, _) => value.toString
      case Value.Int(value, _, _) => value.toString
      case Value.Float(value, _) => value.toString
      case Value.String(value, _) =>
        val escapedString = value
          .replaceAll("([\"\\\\])", "\\\\$1")
          .replaceAllLiterally("\n", "\\n")

        '"' + escapedString + '"'

      case Value.Native(native, _) => s"<${native.toString}>"

      case Value.Struct(struct, _) =>
        val values = struct.props.iterator.map { case (key, value) => s"$key = ${value.inspectAST}" }

        s"$${${values.mkString(", ")}}"

      case Value.Lambda(lambda, _) =>
        val body = lambda.body.inspectAST
        val params = lambda.params.map {
          case Parameter(name, Some(typeValue)) => s"(param $name ${typeValue.inspectAST})"
          case Parameter(name, None) => s"(param $name)"
        }.mkString(" ")

        s"(lambda [$params] $body)"

      case Value.Operation(operation, _) => operation.inspectAST
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

  def isNothing: Boolean = {
    this match {
      case Value.Nothing(_) => true
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
  case class Int(value: scala.Int, location: Option[Location], override val typeObject: Option[TypeObject]) extends Value
  case class Float(value: scala.Double, location: Option[Location]) extends Value
  case class String(value: java.lang.String, location: Option[Location]) extends Value

  case class Native(native: NativeValue, location: Option[Location]) extends Value
  case class Struct(value: photon.Struct, location: Option[Location]) extends Value
  case class Lambda(value: photon.Lambda, location: Option[Location]) extends Value

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value
}

sealed abstract class TypeObject

object TypeObject {
  case class Native(native: NativeValue) extends TypeObject
  case class Struct(struct: photon.Struct) extends TypeObject
}

sealed abstract class LambdaTrait

object LambdaTrait {
  case object Partial extends LambdaTrait
  case object CompileTime extends LambdaTrait
  case object Runtime extends LambdaTrait
  case object Pure extends LambdaTrait
}

case class ObjectId(id: Long) extends AnyVal

object ObjectId {
  val idCounter = new AtomicLong(1)

  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
}

class Variable(val name: String, private var _value: Value) extends Equals {
  val objectId: Long = ObjectId().id

  def value: Value = _value

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Variable]
  override def equals(that: Any): Boolean = {
    that match {
      case other: Variable => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode

  def dangerouslySetValue(newValue: Value): Unit = _value = newValue
}

object Scope {
  def newRoot(variables: Seq[Variable]): Scope = {
    Scope(
      None,
      variables.map { variable => (variable.name, variable) }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[String, Variable]) {
  def newChild(variables: Seq[Variable]): Scope = {
    Scope(
      Some(this),
      variables.map { variable => (variable.name, variable) }.toMap
    )
  }

  override def toString: String = {
    val values = variables.view.mapValues(_.value)

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: String): Option[Variable] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}

case class Struct(props: Map[String, Value]) {
  override def toString: String = Unparser.unparse(this)
}

case class Lambda(params: Seq[Parameter], scope: Scope, body: Operation.Block, traits: Set[LambdaTrait]) {
  override def toString: String = Unparser.unparse(this)
}

sealed abstract class Operation {
  override def toString: String = Unparser.unparse(this)

  def inspectAST: String = {
    this match {
      case Operation.Block(values) =>
        if (values.nonEmpty) {
          s"{ ${values.map(_.inspectAST).mkString(" ")} }"
        } else { "{}" }

      case Operation.Call(target, name, arguments, _) =>
        val positionalArguments = arguments.positional.map(_.inspectAST)
        val namedArguments = arguments.named.map { case (name, value) => s"(param $name ${value.inspectAST})" }
        val argumentStrings = positionalArguments ++ namedArguments

        if (argumentStrings.isEmpty) {
          s"($name ${target.inspectAST})"
        } else {
          s"($name ${target.inspectAST} ${argumentStrings.mkString(" ")})"
        }

      case Operation.NameReference(name) => name

      case Operation.Let(name, value, block) => s"(let $name ${value.inspectAST} ${block.inspectAST})"

      case Operation.LambdaDefinition(params, body) =>
        val bodyAST = body.inspectAST
        val paramsAST = params.map {
          case Parameter(name, Some(typeValue)) => s"(param $name ${typeValue.inspectAST})"
          case Parameter(name, None) => s"(param $name)"
        }.mkString(" ")

        s"(lambda [$paramsAST] $bodyAST)"
    }
  }
}

object Operation {
  case class Block(values: Seq[Value]) extends Operation
  case class Call(target: Value, name: String, arguments: Arguments, mayBeVarCall: Boolean) extends Operation
  case class NameReference(name: String) extends Operation
  case class Let(name: String, value: Value, block: Block) extends Operation
  case class LambdaDefinition(params: Seq[Parameter], body: Operation.Block) extends Operation
}

case class Parameter(name: String, typeValue: Option[Value])

case class Arguments(positional: Seq[Value], named: Map[String, Value]) {
  def withoutSelf = Arguments(positional.drop(1), named)
}

object Arguments {
  val empty: Arguments = Arguments(Seq.empty, Map.empty)
}