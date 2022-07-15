package photon.base

import photon.frontend.ASTValue

class VarName(val originalName: String)

trait Value {
  def location: Option[Location]
  def typ(scope: Scope): Type
  // TODO: Memoize the result
  def evaluate(env: Environment): Value

  def inconvertible = throw EvalError(s"Could not convert $this to AST", location)
  def toAST(names: Map[VarName, String]): ASTValue
}

trait ConcreteValue extends Value {
  override def evaluate(env: Environment): Value = this
}

trait Type extends ConcreteValue {
  override val location = None
  override def toAST(names: Map[VarName, String]): ASTValue = inconvertible

  val methods: Map[String, Method]

  def method(name: String): Option[Method] = methods.get(name)
}

sealed trait EvalMode
object EvalMode {
  // Running code during runtime
  case object RunTime extends EvalMode

  // Running compile-time-only code
  case object CompileTimeOnly extends EvalMode

  // Partially evaluating code in a default function
  case object Partial extends EvalMode

  // Partially evaluating code in a runtime-only function
  case object PartialRunTimeOnly extends EvalMode

  // Partially evaluating code in a prefer-runtime function
  case object PartialPreferRunTime extends EvalMode
}