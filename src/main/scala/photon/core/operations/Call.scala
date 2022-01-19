package photon.core.operations

import photon.compiler.CompilerContext
import photon.core.{Core, MethodTrait, StandardType, TypeRoot}
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location, UOperation}

object Call extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
  override def compile(output: CompilerContext): Unit = uncompilable
}

case class CallValue(name: String, args: Arguments[EValue], location: Option[Location]) extends EValue {
  override val typ = Call
  override def unboundNames =
    args.self.unboundNames ++
    args.positional.flatMap(_.unboundNames).toSet ++
    args.named.values.flatMap(_.unboundNames).toSet

  override def evalMayHaveSideEffects = true // method.traits.contains(MethodTrait.SideEffects)

  override def evalType = Some(method.typeCheck(args))
  override protected def evaluate: EValue = {
    // TODO: Detect if values are fully evaluated or not
    // method.call(args.map(_.evaluated), location)
    method.call(args, location)
  }

//  private lazy val argTypes = args.map { arg => arg.evalType.getOrElse(arg.typ) }

  private lazy val method = {
    val evalType = args.self.evalType
      .getOrElse(args.self.typ)

    evalType.method(name)
      .getOrElse { throw EvalError(s"No method named $name on $evalType (self = ${args.self})", location) }
  }

  override def toUValue(core: Core) = UOperation.Call(name, args.map(_.toUValue(core)), location)

  override def compile(context: CompilerContext): Unit = {
    val cFunctionName = context.requireFunction(method)

    // TODO: Support named arguments
    // TODO: This should support block values
//    val cArgs = args.positional.prepended(args.self).map(_.compile(context))
//    val cArgs = args.toPositional(method.)

    val callArgs = args.positional.prepended(args.self)
    val argNames = callArgs.zipWithIndex.map { case (arg, index) =>
      val argType = arg.evalType.getOrElse(arg.typ)
      val varName = s"${cFunctionName}__arg__$index"

      context.code.append(s"${context.requireType(argType)} $varName;")
      arg.compile(context.returnInto(varName))
    }

    context.appendValue(s"$cFunctionName(${argNames.mkString(", ")})")
  }
}
