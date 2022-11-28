package photon.core.operations

import photon.base._
import photon.core.$ScopeBound
import photon.frontend.{ASTArguments, ASTValue}
import photon.lib.ScalaExtensions.IterableSetExtensions

case class $Call(name: String, args: Arguments[Value], location: Option[Location]) extends Value {
  override def isOperation = true
  override def evalMayHaveSideEffects = true
  override def unboundNames =
    (Seq(args.self) ++ args.positional ++ args.named.values)
      .map(_.unboundNames)
      .unionSets

  override def typ(scope: Scope) =
    findMethod(scope)
      .signature
      .specialize(args.map($ScopeBound(_, scope)), scope) match {
      case Left(value) => value.returnType
      case Right(typeError) => throw typeError
    }

  override def toAST(names: Map[VarName, String]) =
    ASTValue.Call(
      target = args.self.toAST(names),
      name,
      arguments = ASTArguments(
        positional = args.positional.map(_.toAST(names)),
        named = args.named.map { case name -> value => name -> value.toAST(names) }
      ),
      mayBeVarCall = false,
      location
    )

  override def evaluate(env: Environment): Value = {
    val method = findMethod(env.scope)
    val boundArgs = args.map($ScopeBound(_, env.scope))

    // TODO: Memoize and share this between `typ` and `evaluate`
    val spec = method.signature.specialize(boundArgs, env.scope) match {
      case Left(value) => value
      case Right(typeError) => throw typeError
    }

    try {
      val result = method.call(env, spec, location)
      result
    } catch {
      case DelayCall =>
        // TODO: This should be correct, right? Or not? What about self-references here?
        // TODO: This is wrong when doing partial evaluation because the arguments get bound to
        //       the partial scope. These should either be unwrapped from the scope or I should
        //       not use spec.args. But I want spec.args because that may have converted the values
        //       during specialization.
        val evaluatedArgs = spec.args.map(_.evaluate(env))

        $Call(name, evaluatedArgs, location)

      case CannotCallCompileTimeMethodInRunTimeMethod =>
        throw EvalError(s"Cannot call compile-time-only method $name inside of runtime-only method", location)

      case CannotCallRunTimeMethodInCompileTimeMethod =>
        throw EvalError(s"Cannot call run-time-only method $name inside of compile-time-only method", location)
    }
  }

  private def findMethod(scope: Scope) = {
    val selfType = args.self.typ(scope)

    selfType.method(name)
      .getOrElse { throw TypeError(s"Cannot find method $name on $selfType", location) }
  }
}
