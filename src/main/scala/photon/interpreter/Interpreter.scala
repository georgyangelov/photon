package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.core.operations.{BlockValue, CallValue, FunctionDefValue, LetValue, ReferenceValue}
import photon.core.{BoolValue, Core, FloatValue, IntValue, LazyValue, StringValue}
import photon.frontend.{ASTToValue, ASTValue}
import photon.lib.Lazy
import photon.{EValue, Location, PhotonError, Scope, ULiteral, UOperation, UValue, Variable}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

object Interpreter {
  private[this] val threadLocal = ThreadLocal.withInitial[Option[Interpreter]](() => None)

  def current = threadLocal.get.getOrElse { throw EvalError("No current interpreter", None) }

  def withCurrent[T](interpreter: Interpreter)(code: => T) = {
    val old = threadLocal.get

    try {
      threadLocal.set(Some(interpreter))
      code
    } finally {
      threadLocal.set(old)
    }
  }
}

class Interpreter {
  private val logger = Logger[Interpreter]
  val core = new Core

  def evaluate(ast: ASTValue): EValue = {
    val uvalue = ASTToValue.transform(ast, core.staticRootScope)

    evaluate(uvalue)
  }

//  def evaluateInNewSubScope(
//    value: UValue,
//    scope: Scope,
//    locals: Seq[Variable],
//    renames: Map[VariableName, VariableName]
//  ): EValue = {
//    val evalue = toEValue(value, scope, renames)
//
//    evalue.evaluated
//  }

  def evaluate(value: UValue): EValue = Interpreter.withCurrent(this) {
    toEValue(value, core.rootScope).evaluated
  }

  def evaluateToUValue(value: ASTValue): UValue = evaluate(value).toUValue(core)
  def evaluateToUValue(value: UValue): UValue = evaluate(value).toUValue(core)

  def toEValue(value: UValue, scope: Scope): EValue = value match {
    case ULiteral.Nothing(_) => ???
    case ULiteral.Boolean(value, location) => BoolValue(value, location)
    case ULiteral.Int(value, location) => IntValue(value, location)
    case ULiteral.Float(value, location) => FloatValue(value, location)
    case ULiteral.String(value, location) => StringValue(value, location)

    case UOperation.Block(values, location) =>
      val evalues = values.map(toEValue(_, scope))

      BlockValue(evalues, location)


    case UOperation.Let(name, value, block, location) =>
      var innerScope: Option[Scope] = None
      // TODO: This will currently blow the stack if it self-references
      val lazyVal = Lazy.of(() => toEValue(value, innerScope.get))

      innerScope = Some(scope.newChild(Seq(
        Variable(name, LazyValue(lazyVal, location))
      )))

      val evalue = lazyVal.resolve
      val eblock = toEValue(block, innerScope.get)

      LetValue(name, evalue, eblock, location)


    case UOperation.Reference(name, location) =>
      val variable = scope.find(name) match {
        case Some(value) => value
        case None => throw EvalError(s"Cannot find name ${name.originalName} in scope $scope", location)
      }

      ReferenceValue(variable, location)


    case UOperation.Function(fn, location) =>
      FunctionDefValue(fn, scope, location)

    case UOperation.Call(name, arguments, location) =>
      CallValue(name, arguments.map(toEValue(_, scope)), location)
  }
}
