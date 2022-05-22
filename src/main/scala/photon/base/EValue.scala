package photon.base

import photon.Core
import photon.core.operations.$Call
import photon.frontend.{UPattern, UValue}

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

case class EValueContext(
  interpreter: Interpreter,
  evalMode: EvalMode,
  cache: EvalCache
) {
  def core = interpreter.core
  def toEValue(uvalue: UValue, scope: Scope) = interpreter.toEValue(uvalue, scope)
  def toEPattern(uvalue: UPattern, scope: Scope) = interpreter.toEPattern(uvalue, scope)
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

  // TODO: Custom `$equals` method in objects
  def equals(a: EValue, b: EValue, location: Option[Location]): EValue =
    $Call.Value("==", Arguments.positional(a, Seq(b)), location).evaluated
}

trait EValue {
  def inspect: String = toUValue(EValue.context.core).toString

  /**
   * The location of the current value in the originally-parsed source code.
   * This is typically used for error messages.
   */
  val location: Option[Location]

  /**
   * Is this an operation?
   * This will be used to check if some expressions are fully evaluated.
   */
  def isOperation = false

  /**
   * Names which this value or values embedded in it reference.
   * It should include references of inner values because this will be used
   * by let-elimination (eliminating unused let definitions).
   *
   * For example, classes, lists, functions, etc. should provide this and
   * pass through to inner values.
   */
  def unboundNames: Set[VariableName]

  /**
   * Converts EValue to UValue so that it can be further converted back to AST.
   */
  def toUValue(core: Core): UValue
  protected def inconvertible =
    throw EvalError(s"Cannot convert value of class ${this.getClass.getName} to UValue", location)

  /**
   * Can the evaluation of this value cause side-effects?
   *
   * It's fine to have this set as `true` even if side-effects may happen
   * only in some cases.
   */
  def evalMayHaveSideEffects: Boolean

  /**
   * The type of the current value
   */
  def typ: Type

  /**
   * The type of value that will be produced if the current one fully
   * evaluates. For example, this would be `Int` in an EValue `Call` which,
   * when called, returns an Int. This method will be used to get the type
   * for type checking and for finding methods to call.
   */
  def realType: Type

  /**
   * Computes and returns the evaluated result of this value.
   *
   * This method calls `evaluate(EvalMode)` internally and caches
   * the result per-EvalMode. So calls for a single EvalMode are computed
   * only once but it may get executed multiple times if the EvalMode is
   * different.
   *
   * This method uses EValue.context.evalMode to look for the current EvalMode.
   */
  def evaluated: EValue = {
    val context = EValue.context

    context.cache.evaluate(this, context.evalMode)
  }

  /**
   * Same as `evaluated`, but sets the EvalMode in the current context first.
   */
  def evaluated(mode: EvalMode): EValue =
    EValue.withContext(EValue.context.copy(evalMode = mode)) { evaluated }

  /**
   * An internal-only method, please use `evaluated` instead.
   *
   * This method does the actual evaluation and DOES NOT perform caching
   * as `evaluated` does. Additionally, it DOES NOT set the EvalMode in
   * the context.
   */
  protected[base] def evaluate(mode: EvalMode): EValue

  /**
   * Returns the inner real value even if it's wrapped in Let expressions.
   * Remembers the Let expressions it depends on and is able to wrap them back.
   *
   * This is typically used if some method needs to be able to inspect the value
   * even if it's not fully known.
   *
   * @param followReferences Whether to follow any references
   */
  def partialValue(followReferences: Boolean): PartialValue = PartialValue(this, Seq.empty)

  /**
   * Tries to evaluate the value fully (evaluates it in CompileTimeOnly mode)
   * and checks if it is a Type.
   *
   * Returns it if it is or throws an error if it isn't.
   */
  def assertType =
    this.evaluated(EvalMode.CompileTimeOnly) match {
      case value: Type => value
      case _ => throw EvalError(s"Invalid value $this, expected a Type", location)
    }

  /**
   * Tries to evaluate the value fully (evaluates it in CompileTimeOnly mode)
   * and checks if it is a specific type of Type.
   *
   * Returns it if it is or throws an error if it isn't.
   */
  def assertSpecificType[T <: Type](implicit tag: ClassTag[T]) =
    this.evaluated(EvalMode.CompileTimeOnly) match {
      case value: T => value
      case _ => throw EvalError(s"Invalid value $this, expected a $tag value", location)
    }
}

/**
 * An EValue which can't be evaluated further - it is a fixed
 * value which doesn't change.
 */
trait RealEValue extends EValue {
  override def realType = typ
  override def evalMayHaveSideEffects = false
  override def evaluated: EValue = this
  override def evaluated(mode: EvalMode): EValue = this
  override def evaluate(mode: EvalMode): EValue = this
}

//trait EValue {
//  def inspect: String = toUValue(EValue.context.core).toString
//  def isOperation = false
//
//  def typ: Type
//  def unboundNames: Set[VariableName]
//  val location: Option[Location]
//
//  def toUValue(core: Core): UValue
//  protected def inconvertible =
//    throw EvalError(s"Cannot convert value of class ${this.getClass.getName} to UValue", location)
//
//  // TODO: Cache based on EvalMode. Otherwise we may end up calling side-effectful functions more than once
//  //  def evaluated = evaluate(evalMode)
//  //  lazy val evaluated: EValue = evaluate
//  def evaluated = evaluate
//  def evaluated(evalMode: EvalMode) =
//    EValue.withContext(EValue.context.copy(evalMode = evalMode)) { evaluate }
//
//  protected def evaluate: EValue
//
//  // TODO: Better name? Finalize? Compile? Optimize?
//  def finalEval: EValue
//
//  def partialValue(followReferences: Boolean): PartialValue = PartialValue(this, Seq.empty)
//  def inlinedValue: EValue = this
//
//  def evalType: Option[Type]
//  def evalMayHaveSideEffects: Boolean
//
//  def assertType =
//    this.evaluated(EvalMode.CompileTimeOnly) match {
//      case value: Type => value
//      case _ => throw EvalError(s"Invalid value $this, expected a Type", location)
//    }
//
//  def assertSpecificType[T <: Type](implicit tag: ClassTag[T]) =
//    this.evaluated(EvalMode.CompileTimeOnly) match {
//      case value: T => value
//      case _ => throw EvalError(s"Invalid value $this, expected a $tag value", location)
//    }
//}