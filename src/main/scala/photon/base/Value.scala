package photon.base

import photon.frontend.ASTValue

import scala.reflect.ClassTag

class VarName(val originalName: String) {
  override def toString = s"VarName($originalName)"
}

trait Value {
  def isOperation: Boolean = false
  def evalMayHaveSideEffects: Boolean
  def location: Option[Location]
  def unboundNames: Set[VarName]
  def typ(scope: Scope): Type

  // TODO: Memoize the result?
  def evaluate(env: Environment): Value

  def inconvertible = throw EvalError(s"Could not convert $this to AST", location)
  def toAST(names: Map[VarName, String]): ASTValue

  def asType: Type = this match {
    case typ: Type => typ
    case _ => throw EvalError(s"Expected value $this to be a type", location)
  }

  def partialValue(env: Environment, followReferences: Boolean): PartialValue = PartialValue(this, Seq.empty)
}

trait ConcreteValue extends Value {
  override def evalMayHaveSideEffects = false
  override def unboundNames = Set.empty
  override def evaluate(env: Environment): Value = this
}

trait Type extends ConcreteValue {
  override val location: Option[Location] = None
  override def toAST(names: Map[VarName, String]): ASTValue = inconvertible

  val methods: Map[String, Method]

  def method(name: String): Option[Method] = methods.get(name)

  def isSameAs(other: Type) = this.resolveLazy == other.resolveLazy

  protected def resolveLazy: Type = this
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