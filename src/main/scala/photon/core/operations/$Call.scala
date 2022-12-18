package photon.core.operations

import photon.base._
import photon.core.$ScopeBound
import photon.core.objects.$AnyStatic
import photon.frontend.{ASTArguments, ASTValue}
import photon.lib.ScalaExtensions.IterableSetExtensions

case class $Call(name: String, args: Arguments[Value], location: Option[Location]) extends Value {
  override def isOperation = true
  override def evalMayHaveSideEffects = true
  override def unboundNames =
    (Seq(args.self) ++ args.positional ++ args.named.values)
      .map(_.unboundNames)
      .unionSets

  override def typ(scope: Scope) = {
    // TODO: Need to eval self here if it's $AnyStatic
    findMethod(args.self.typ(scope))
      .signature
      .specialize(args.map($ScopeBound(_, scope)), scope) match {
      case Left(value) => value.returnType
      case Right(typeError) => throw typeError
    }
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

  override def evaluate(env: Environment) = {
    // TODO: Should I do this with the other arguments as well?
    val (self, selfType) = args.self.typ(env.scope).resolvedType match {
      case value if value == $AnyStatic =>
        // Should be ok to skip closures here because it's a compile-time only eval value
        val EvalResult(evaluatedSelf, _) = args.self.evaluate(env)

        (evaluatedSelf, evaluatedSelf.typ(env.scope))

      case typ => (args.self, typ)
    }

    val method = findMethod(selfType)

    // TODO: Memoize and share this between `typ` and `evaluate`
    val spec = method.signature.specialize(args.changeSelf(self), env.scope) match {
      case Left(value) => value
      case Right(typeError) => throw typeError
    }

    try {
//      val boundArgs = args.map($ScopeBound(_, env.scope))
      val result = method.call(env, spec, location)

      result
    } catch {
      case DelayCall =>
        // TODO: This should be correct, right? Or not? What about self-references here?
        val evaluatedArgs = spec.args.changeSelf(self).map(_.evaluate(env))
        val closures = evaluatedArgs.values.flatMap(_.closures)

        val result = $Call(name, evaluatedArgs.map(_.value), location)

        EvalResult(result, closures)

      case CannotCallCompileTimeMethodInRunTimeMethod =>
        throw EvalError(s"Cannot call compile-time-only method $name inside of runtime-only method", location)

      case CannotCallRunTimeMethodInCompileTimeMethod =>
        throw EvalError(s"Cannot call run-time-only method $name inside of compile-time-only method", location)
    }
  }

  private def findMethod(selfType: Type) = {
    selfType.method(name)
      .getOrElse { throw TypeError(s"Cannot find method $name on ${selfType.resolvedType}", location) }
  }
}
