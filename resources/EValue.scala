package photon

import photon.core.operations.LetValue
import photon.interpreter.{EvalError, Interpreter}
import photon.core.{Core, Type}

import scala.reflect.ClassTag

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

object EValue {
  private val _current = ThreadLocal.withInitial[Option[EValueContext]](() => None)

  def context = _current.get.getOrElse { throw new Exception("No EValueContext") }
  def withContext[T](context: EValueContext)(code: => T) = {
    val old = _current.get

    try {
      _current.set(Some(context))
      code
    } finally {
      _current.set(old)
    }
  }
}

case class EValueContext(
  interpreter: Interpreter,
  evalMode: EvalMode
) {
  def core = interpreter.core
  def toEValue(uvalue: UValue, scope: Scope) = interpreter.toEValue(uvalue, scope)
}

trait EValue {
  def inspect: String = toUValue(EValue.context.core).toString
  def isOperation = false

  def typ: Type
  def unboundNames: Set[VariableName]
  val location: Option[Location]

  def toUValue(core: Core): UValue
  protected def inconvertible =
    throw EvalError(s"Cannot convert value of class ${this.getClass.getName} to UValue", location)

  // TODO: Cache based on EvalMode. Otherwise we may end up calling side-effectful functions more than once
//  def evaluated = evaluate(evalMode)
//  lazy val evaluated: EValue = evaluate
  def evaluated = evaluate
  def evaluated(evalMode: EvalMode) =
    EValue.withContext(EValue.context.copy(evalMode = evalMode)) { evaluate }

  protected def evaluate: EValue

  // TODO: Better name? Finalize? Compile? Optimize?
  def finalEval: EValue

  def partialValue(followReferences: Boolean): PartialValue = PartialValue(this, Seq.empty)
  def inlinedValue: EValue = this

  def evalType: Option[Type]

  def assertType =
    this.evaluated(EvalMode.CompileTimeOnly) match {
      case value: Type => value
      case _ => throw EvalError(s"Invalid value $this, expected a Type", location)
    }

  def assertSpecificType[T <: Type](implicit tag: ClassTag[T]) =
    this.evaluated(EvalMode.CompileTimeOnly) match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value $this, expected a $tag value", location)
    }
}

case class PartialValue(value: EValue, variables: Seq[Variable]) {
  def replaceWith(newValue: EValue) = PartialValue(newValue, variables)
  def addOuterVariables(additionalVars: Seq[Variable]) = PartialValue(value, additionalVars ++ variables)
  def addInnerVariables(additionalVars: Seq[Variable]) = PartialValue(value, variables ++ additionalVars)
  def withOuterVariable(variable: Variable) = PartialValue(value, variables :+ variable)

  def wrapBack: EValue =
    variables.foldRight(value) { case (Variable(name, varValue), innerValue) =>
      if (innerValue.unboundNames.contains(name)) {
        LetValue(name, varValue, innerValue, varValue.location)
      } else {
        innerValue
      }
    }
}