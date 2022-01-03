package photon.interpreter

import com.typesafe.scalalogging.Logger
import photon.core2.operations.BlockValue
import photon.core2.{BoolValue, FloatValue, IntValue, LazyValue, StringValue}
import photon.lib.{Eager, Lazy}
import photon.{Arguments, EValue, Location, PhotonError, Scope, ULiteral, UOperation, UValue, Variable}

case class EvalError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

case class CallContext(
  interpreter: Interpreter,
//  runMode: RunMode,
//  callStack: Seq[CallStackEntry],
//  callScope: Scope
) {
  def callOrThrow(target: EValue, name: String, args: Arguments[EValue], location: Option[Location]) = {
    target.typ.method(name).getOrElse {
      throw EvalError(s"There is no method $name on ${target.typ}", location)
    }.call(this, args, location)
  }
}

class Interpreter2 {
  private val logger = Logger[Interpreter]

  def evaluate(value: UValue, scope: Scope): EValue = value match {
    case ULiteral.Nothing(_) => ???
    case ULiteral.Boolean(value, location) => BoolValue(value, location)
    case ULiteral.Int(value, location) => IntValue(value, location)
    case ULiteral.Float(value, location) => FloatValue(value, location)
    case ULiteral.String(value, location) => StringValue(value, location)

    case UOperation.Block(values, location) =>
      val lastValueIndex = values.length
      val evalues = values.map(evaluate(_, scope))
        .zipWithIndex
        .filter { case (value, index) => index == lastValueIndex || value.mayHaveSideEffects }
        .map(_._1)

      if (evalues.length == 1) {
        evalues.head
      } else {
        BlockValue(evalues, location)
      }


    case UOperation.Let(name, value, block, location) =>
      var innerScope: Option[Scope] = None
      // TODO: This will currently blow the stack if it self-references
      val lazyVal = Lazy.of(() => evaluate(value, innerScope.get))

      innerScope = Some(scope.newChild(Seq(
        Variable(name, LazyValue(lazyVal, location))
      )))

      // Triggering the lazy eval to make sure any side-effect code has run
      val evalue = lazyVal.resolve
      val eblock = evaluate(block, innerScope.get)

      // TODO: Eliminate variable assignment if it's unused in the block.
      //       Also add any potential side-effects to the block from the value assignment.

      if (lazyVal.resolve.mayHaveSideEffects) {
        // TODO: Not doing the block-value optimization from the previous case here, but shouldn't matter
        //       as the first value may have side effects and we need the second one for the return type
        BlockValue(Seq(evalue, eblock), location)
      } else {
        eblock
      }


    case UOperation.Reference(name, location) =>
      val referencedValue = scope.find(name) match {
        case Some(value) => value
        case None => throw EvalError(s"Cannot find name ${name.originalName} in scope $scope", location)
      }

      ReferenceValue()


    case UOperation.Function(fn, location) => ???
    case UOperation.Call(name, arguments, location) => ???
  }
}
