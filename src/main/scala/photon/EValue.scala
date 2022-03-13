package photon

import photon.interpreter.{EvalError, Interpreter}
import photon.core.{Core, MethodRunMode, Type}

import scala.reflect.ClassTag

sealed trait EvalMode
object EvalMode {
  object CompileTime extends EvalMode
  case class Partial(runMode: MethodRunMode) extends EvalMode
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

  // TODO: Cache based on EvalMode? Or cache just in Reference
//  def evaluated = evaluate(evalMode)
//  lazy val evaluated: EValue = evaluate
  def evaluated = evaluate
  // TODO: Add EvalMode here as argument (or EValueContext)
  protected def evaluate: EValue

  // TODO: Better name? Finalize? Compile? Optimize?
  def finalEval: EValue

  def evalType: Option[Type]
  def evalMayHaveSideEffects: Boolean

  def evalCheck[T <: EValue](implicit tag: ClassTag[T]): Option[T] = {
    val context = EValueContext(
      interpreter = EValue.context.interpreter,
      evalMode = EvalMode.CompileTime
    )

    EValue.withContext(context) {
      this.evaluate match {
        case value: T => Some(value)
        case _ => None
      }
    }
  }

  def evalAssert[T <: EValue](implicit tag: ClassTag[T]) =
    evalCheck[T](tag) match {
      case Some(value) => value
      case _ => throw EvalError(s"Invalid value type $this, expected a $tag value", location)
    }

  def assert[T <: EValue](implicit tag: ClassTag[T]) =
    this match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value type $this, expected a $tag value", location)
    }
}