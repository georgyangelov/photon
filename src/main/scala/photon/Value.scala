package photon

import photon.core.NativeValue

import java.util.concurrent.atomic.AtomicLong
import scala.collection.Map

sealed abstract class Value {
  def location: Option[Location]
  def typeObject: Option[TypeObject] = None

  override def toString: String = Unparser.unparse(this)

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
}

object Value {
  case class Unknown(location: Option[Location]) extends Value
  case class Nothing(location: Option[Location]) extends Value

  case class Boolean(value: scala.Boolean, location: Option[Location]) extends Value
  case class Int(value: scala.Int, location: Option[Location], override val typeObject: Option[TypeObject]) extends Value
  case class Float(value: scala.Double, location: Option[Location]) extends Value
  case class String(value: java.lang.String, location: Option[Location]) extends Value

  case class Native(native: NativeValue, location: Option[Location]) extends Value
  case class Struct(struct: photon.Struct, location: Option[Location]) extends Value
  case class BoundFunction(fn: photon.BoundFunction, location: Option[Location]) extends Value

  case class Operation(operation: photon.Operation, location: Option[Location]) extends Value
}

sealed abstract class TypeObject

object TypeObject {
  case class Native(native: NativeValue) extends TypeObject
  case class Struct(struct: photon.Struct) extends TypeObject
}

sealed abstract class FunctionTrait

object FunctionTrait {
  case object Partial extends FunctionTrait
  case object CompileTime extends FunctionTrait
  case object Runtime extends FunctionTrait
  case object Pure extends FunctionTrait
}

case class ObjectId(id: Long) extends AnyVal

object ObjectId {
  val idCounter = new AtomicLong(1)

  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
}

class VariableName(val originalName: String) extends Equals {
  val objectId: Long = ObjectId().id

  override def canEqual(that: Any): Boolean = that.isInstanceOf[VariableName]
  override def equals(that: Any): Boolean = {
    that match {
      case other: VariableName => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

class Variable(val name: String, private var _value: Value) extends Equals {
  def value: Value = _value

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

case class BoundFunction(
  fn: Function,
  scope: Scope,

  traits: Set[FunctionTrait]
)

class Function(
  val params: Seq[Parameter],
  val unboundVariables: Set[VariableName],
  val body: Value
) extends Equals {
  val objectId = ObjectId()

  override def toString: String = Unparser.unparse(this)

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Function]
  override def equals(that: Any): Boolean = {
    that match {
      case other: Function => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

sealed abstract class Operation {
  override def toString: String = Unparser.unparse(this)
}

object Operation {
  case class Block(values: Seq[Value]) extends Operation

  case class Let(variable: VariableName, value: Value, block: Block) extends Operation
  case class Reference(name: VariableName) extends Operation

  case class Function(fn: photon.Function) extends Operation
  case class Call(target: Value, name: String, arguments: Arguments) extends Operation
}

case class Parameter(name: String, typeValue: Option[Value])

case class Arguments(positional: Seq[Value], named: Map[String, Value]) {
  def withoutSelf = Arguments(positional.drop(1), named)
}

object Arguments {
  val empty: Arguments = Arguments(Seq.empty, Map.empty)
}

case class CallStackEntry(methodId: ObjectId, name: String, location: Option[Location])
