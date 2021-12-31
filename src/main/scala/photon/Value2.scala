package photon

import photon.interpreter.EvalError
import photon.core2.Type

import scala.reflect.ClassTag

sealed trait Value {}

sealed trait UValue {
  val location: Option[Location]
}

object ULiteral {
  case class Nothing(                         location: Option[Location]) extends UValue {}
  case class Boolean(value: scala.Boolean,    location: Option[Location]) extends UValue {}
  case class Int(    value: scala.Int,        location: Option[Location]) extends UValue {}
  case class Float(  value: scala.Double,     location: Option[Location]) extends UValue {}
  case class String( value: java.lang.String, location: Option[Location]) extends UValue {}
}

object UOperation {
  case class Block(
    values: Seq[UValue],
    location: Option[Location]
  ) extends UValue

  case class Let(
    name: VariableName,
    value: UValue,
    block: UOperation.Block,
    location: Option[Location]
  ) extends UValue

  case class Reference(
    name: VariableName,
    location: Option[Location]
  ) extends UValue

  case class Function(
    fn: photon.Function,
    location: Option[Location]
  ) extends UValue

  case class Call(
    name: String,
    arguments: Arguments[UValue],
    location: Option[Location]
  ) extends UValue
}

trait EValue {
  val typ: Type
  val location: Option[Location]

  def assert[T <: EValue](implicit tag: ClassTag[T]) =
    this match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value type $this, expected a native value", this.location)
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
        case None => throw EvalError(s"Missing argument ${name} (at index ${index})", None)
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
  def empty[T <: Value](self: T): Arguments[T] = Arguments(self, Seq.empty, Map.empty)

  def positional[T <: Value](self: T, values: Seq[T]) = Arguments[T](
    self = self,
    positional = values,
    named = Map.empty
  )
}
