package photon.core.operations

import photon.base._
import photon.core.$ScopeBound
import photon.core.objects.$AnyStatic
import photon.frontend.{ASTArguments, ASTValue}
import photon.lib.ScalaExtensions.IterableSetExtensions

case class $Call(name: String, args: Arguments[Value], location: Option[Location]) extends Value {
  case class StaticCallDescription(
    method: Method,
    callSpec: CallSpec
  )

  override def isOperation = true
  override def evalMayHaveSideEffects = true
  override def unboundNames =
    (Seq(args.self) ++ args.positional ++ args.named.values)
      .map(_.unboundNames)
      .unionSets

  override def typ(scope: Scope) = staticCallDescription(scope).callSpec.returnType

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

  private def staticCallDescription(scope: Scope): StaticCallDescription = {
    val env = Environment(scope, EvalMode.CompileTimeOnly)
    val argsWithTypes = args.map(valueWithType(_, env))
    val method = findMethod(argsWithTypes.self._2)

    val concreteSignature = method.signature
      .instantiate(argsWithTypes.withoutSelf.map(_._2)) match {
      case Left(value) => value
      case Right(typeError) => throw typeError
    }

    concreteSignature.specialize(argsWithTypes) match {
      case Left(callSpec) => StaticCallDescription(method, callSpec)
      case Right(typeError) => throw typeError
    }
  }

  private def valueWithType(value: Value, env: Environment): (Value, Type) =
    value.typ(env.scope).resolvedType match {
      case valueType if valueType == $AnyStatic =>
        // Should be ok to skip closures here because it's a compile-time only eval value
        val EvalResult(evaluatedSelf, _) = value.evaluate(env)

        (evaluatedSelf, evaluatedSelf.typ(env.scope))

      case typ => (value, typ)
    }

  override def evaluate(env: Environment) = {
    // TODO: Memoize and share this between `typ` and `evaluate`
    val StaticCallDescription(method, spec) = staticCallDescription(env.scope)

    try {
      val result = method.call(env, spec, location)

      result
    } catch {
      case DelayCall =>
        // TODO: This should be correct, right? Or not? What about self-references here?
        val evaluatedArgs = spec.args.map(_.evaluate(env))
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
