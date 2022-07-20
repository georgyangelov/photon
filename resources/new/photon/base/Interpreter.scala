package photon.base

import photon.Core
import photon.core._
import photon.core.operations._
import photon.frontend._
import photon.lib.Lazy

class Interpreter {
  val core = new Core

  def withContext[T](evalMode: EvalMode)(code: => T) =
    EValue.withContext(EValueContext(this, evalMode, new EvalCache))(code)

  def toEValueInRootScope(ast: ASTValue): EValue = {
    val uvalue = ASTToUValue.transform(ast, core.staticRootScope)

    toEValue(uvalue, core.rootScope)
  }

  def toEValue(uvalue: UValue, scope: Scope): EValue = uvalue match {
    case ULiteral.Nothing(location) => ???

    case ULiteral.Boolean(value, location) => $Bool.Value(value, location)
    case ULiteral.Int(value, location) => $Int.Value(value, location)
    case ULiteral.Float(value, location) => $Float.Value(value, location)
    case ULiteral.String(value, location) => $String.Value(value, location)

    case UOperation.Block(values, location) =>
      $Block.Value(values.map(toEValue(_, scope)), location)

    case UOperation.Let(name, value, block, location) =>
      var newScope: Option[Scope] = None
      val lazyValue = Lazy.of(() => toEValue(value, newScope.get))
      val eLazyValue = $Lazy.Value(lazyValue, value.location)
      val variable = Variable(new EVarName(name.originalName), eLazyValue)

      newScope = Some(scope.newChild(Seq(name -> variable)))

      val eValue = lazyValue.resolve
      val eBlock = toEValue(block, newScope.get)

      $Let.Value(variable.name, eValue, eBlock, location)

    case UOperation.Reference(name, location) =>
      val variable = scope.find(name).getOrElse {
        throw EvalError(s"Cannot find name '${name.originalName}'", location)
      }

      // TODO: Should the reference contain the whole Variable?
      $Reference.Value(variable.name, variable.value, location)

    case UOperation.Function(fn, location) => $FunctionDef.Value(fn, scope, location)

    case UOperation.Call(name, arguments, location) =>
      val args = arguments.map(uvalue => toEValue(uvalue, scope))

      $Call.Value(name, args, location)

    case pattern: UPattern => toEValue(pattern, scope)._1
  }

  def toEValue(upattern: UPattern, scope: Scope): ($Pattern.Value, Scope) = ???
}
