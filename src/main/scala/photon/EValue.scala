package photon

import photon.interpreter.EvalError
import photon.core.{Core, Type}

import scala.reflect.ClassTag

trait EValue {
  def typ: Type
  def unboundNames: Set[VariableName]
  val location: Option[Location]

  def toUValue(core: Core): UValue
  protected def inconvertible =
    throw EvalError(s"Cannot convert value of class ${this.getClass.getName} to UValue", location)

//  def unbind(from: Scope, to: Scope, renames: Map[VariableName, VariableName]): EValue

  lazy val evaluated: EValue = evaluate
  protected def evaluate: EValue

  def evalType: Option[Type]
  def evalMayHaveSideEffects: Boolean

  def evalCheck[T <: EValue](implicit tag: ClassTag[T]): Option[T] =
    this.evaluated match {
      case value: T => Some(value)
      case _ => None
    }

  def evalAssert[T <: EValue](implicit tag: ClassTag[T]) =
    this.evaluated match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value type $this, expected a $tag value", location)
    }

  def assert[T <: EValue](implicit tag: ClassTag[T]) =
    this match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value type $this, expected a $tag value", location)
    }
}