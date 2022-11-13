package photon.core

import photon.base._
import photon.core.operations.Closure
import photon.frontend.ASTValue

import scala.reflect.ClassTag

case class $Object(obj: Any, typ: Type, location: Option[Location]) extends Value {
  override def evalMayHaveSideEffects = false
  override def typ(scope: Scope) = typ
  
  override def toAST(names: Map[VarName, String]): ASTValue = obj match {
    case value: Int => ASTValue.Int(value, location)
    case value: Float => ASTValue.Float(value, location)
    case value: String => ASTValue.String(value, location)
    case value: Boolean => ASTValue.Boolean(value, location)
    case value: Closure => value.fnDef.toAST(names)
    case _ => inconvertible
  }

  def assert[T <: Any](implicit tag: ClassTag[T]) =
    obj match {
      case value: T => value
      // TODO: Better messaging
      case _ => throw EvalError(s"Unexpected object type, expected $tag, got $obj", location)
    }

  override def unboundNames = obj match {
    case closure: Closure => closure.fnDef.unboundNames
    case _ => Set.empty
  }

  override def evaluate(env: Environment) = this
}