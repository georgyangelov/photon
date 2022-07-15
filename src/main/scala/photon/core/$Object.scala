package photon.core

import photon.base._
import photon.frontend.ASTValue

case class $Object(obj: Any, typ: Type, location: Option[Location]) extends Value {
  override def evaluate(scope: Scope, evalMode: EvalMode) = this
  override def typ(scope: Scope) = typ

  override def toAST(names: Map[VarName, String]): ASTValue = obj match {
    case value: Int => ASTValue.Int(value, location)
    case value: Float => ASTValue.Float(value, location)
    case value: String => ASTValue.String(value, location)
    case value: Boolean => ASTValue.Boolean(value, location)
    case _ => inconvertible
  }
}

object $Int extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(

  )
}