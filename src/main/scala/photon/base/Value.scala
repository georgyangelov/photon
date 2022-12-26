package photon.base

import photon.core.operations.Closure
import photon.frontend.ASTValue

import scala.reflect.ClassTag

class VarName(val originalName: String) {
  override def toString = s"VarName($originalName)"
}

case class EvalResult[T](
  value: T,
  closures: Seq[Closure]
) {
  def partiallyEvaluateInnerClosures(env: Environment): Unit = {
    val uniqueClosures = closures.toSet

    uniqueClosures.foreach(_.evaluatePartially(env))
  }

  def mapValue[R](fn: T => R): EvalResult[R] =
    EvalResult(fn(value), closures)
}

object Value {
  type Wrapper = Value => Value
}

trait Value {
  def isOperation: Boolean = false
  def evalMayHaveSideEffects: Boolean
  def location: Option[Location]
  def unboundNames: Set[VarName]
  def typ(scope: Scope): Type

  // TODO: Memoize the result?
  def evaluate(env: Environment): EvalResult[Value]

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
  override def evaluate(env: Environment) = EvalResult(this, Seq.empty)
}

trait Type extends ConcreteValue {
  override val location: Option[Location] = None
  override def toAST(names: Map[VarName, String]): ASTValue = inconvertible

  val methods: Map[String, Method]

  def method(name: String): Option[Method] = methods.get(name)

  def resolvedType: Type = this
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