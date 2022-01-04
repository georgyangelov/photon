package photon

import photon.interpreter.EvalError
import photon.core.{Core, Type}
import photon.frontend.{Unparser, ValueToAST}
import photon.lib.ObjectId
import photon.lib.ScalaExtensions._

import scala.reflect.ClassTag

sealed trait UValue {
  val location: Option[Location]
  val unboundNames: Set[VariableName]

  override def toString = Unparser.unparse(ValueToAST.transform(this))
}

object ULiteral {
  case class Nothing(location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Boolean(value: scala.Boolean, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Int(value: scala.Int, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Float(value: scala.Double, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class String(value: java.lang.String, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
}

object UOperation {
  case class Block(
    values: Seq[UValue],
    location: Option[Location]
  ) extends UValue {
    override lazy val unboundNames = values.map(_.unboundNames).unionSets
  }

  case class Let(
    name: VariableName,
    value: UValue,
    block: UValue,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = (value.unboundNames ++ block.unboundNames) - name
  }

  case class Reference(
    name: VariableName,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = Set(name)
  }

  case class Function(
    fn: photon.UFunction,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = fn.body.unboundNames -- fn.params.map(_.name)
  }

  case class Call(
    name: String,
    arguments: Arguments[UValue],
    location: Option[Location]
  ) extends UValue {
    override val unboundNames =
      arguments.self.unboundNames ++
      arguments.positional.map(_.unboundNames).unionSets ++
      arguments.named.values.map(_.unboundNames).unionSets
  }
}

trait EValue {
  def typ: Type
  val location: Option[Location]

  lazy val evaluated: EValue = evaluate

  def evalType: Option[Type]
  def evalMayHaveSideEffects: Boolean

  def toUValue(core: Core): UValue
  protected def inconvertible =
    throw EvalError(s"Cannot convert value of class ${this.getClass.getName} to UValue", location)

  protected def evaluate: EValue

  def assert[T <: EValue](implicit tag: ClassTag[T]) =
    this match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value type $this, expected a $tag value", location)
    }
}



class VariableName(val originalName: String) extends Equals {
  private val objectId: Long = ObjectId().id

  def uniqueId = objectId

  override def canEqual(that: Any): Boolean = that.isInstanceOf[VariableName]
  override def equals(that: Any): Boolean = {
    that match {
      case other: VariableName => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

case class Variable(name: VariableName, value: EValue)

object Scope {
  def newRoot(variables: Seq[Variable]): Scope = {
    Scope(
      None,
      variables.map { variable => variable.name -> variable }.toMap
    )
  }
}

case class Scope(parent: Option[Scope], variables: Map[VariableName, Variable]) {
  def newChild(variables: Seq[Variable]): Scope = {
    Scope(
      Some(this),
      variables.map { variable => variable.name -> variable }.toMap
    )
  }

  override def toString: String = {
    val values = variables.map { case name -> variable => name.originalName -> variable.value.toString }

    if (parent.isDefined) {
      s"$values -> ${parent.get.toString}"
    } else {
      values.toString
    }
  }

  def find(name: VariableName): Option[Variable] = {
    variables.get(name) orElse { parent.flatMap(_.find(name)) }
  }
}



case class Arguments[+T](
  self: T,
  positional: Seq[T],
  named: Map[String, T]
) {
  def changeSelf[R >: T](value: R) = Arguments[R](value, positional, named)

  def map[R](f: T => R) = Arguments(
    f(self),
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def forall(f: T => Boolean) = f(self) && positional.forall(f) && named.view.values.forall(f)

  def get(index: Int, name: String): T = {
    if (index == 0) {
      self
    } else if (index - 1 < positional.size) {
      positional(index - 1)
    } else {
      named.get(name) match {
        case Some(value) => value
        case None => throw EvalError(s"Missing argument $name (at index $index)", None)
      }
    }
  }

//  override def toString = Unparser.unparse(
//    ValueToAST.transformForInspection(
//      this.asInstanceOf[Arguments[Value]]
//    )
//  )
}

object Arguments {
  def empty[T](self: T): Arguments[T] = Arguments(self, Seq.empty, Map.empty)

  def positional[T](self: T, values: Seq[T]) = Arguments[T](
    self = self,
    positional = values,
    named = Map.empty
  )
}



class UFunction(
  val params: Seq[UParameter],
  val body: UValue,
  val returnType: Option[UValue]
) extends Equals {
  val objectId = ObjectId()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[UFunction]
  override def equals(that: Any): Boolean = {
    that match {
      case other: UFunction => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

// TODO: Type here should be optional as it may rely on the usage
case class UParameter(name: VariableName, typ: UValue, location: Option[Location])