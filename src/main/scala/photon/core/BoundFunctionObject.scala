package photon.core

import com.typesafe.scalalogging.Logger
import photon.{BoundFunction, EvalError, FunctionTrait, RunMode, Value, Variable}

object FunctionParams {
  val Self: Parameter = Parameter(0, "self")
}

case class BoundFunctionObject(boundFn: BoundFunction) extends NativeObject(Map(
  "call" -> ScalaVarargMethod({ (c, args, l) =>
    if (args.positional.size - 1 + args.named.size != boundFn.fn.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    val lambdaParamNames = boundFn.fn.params.map { param => param.name }

    val positionalParams = lambdaParamNames.zip(args.positional.drop(1))
    val namesOfNamedParams = lambdaParamNames.drop(args.positional.size - 1).toSet

    val namedParams = namesOfNamedParams.map { name =>
      // TODO: This should use the name of the actual parameter always, it should not try to rename it.
      //       This means parameters may have an "external" name and an "internal" name, probably something
      //       like `sendEmail = (to as address, subject) { # This uses address and not to }; sendEmail(to = 'email@example.com')`
      args.named.get(name.originalName) match {
        case Some(value) => (name, value)
        case None => throw EvalError(s"Argument ${name} not specified in method call", l)
      }
    }

    val positionalVariables = positionalParams.map { case (name, value) => new Variable(name, value) }
    val namedVariables = namedParams.map { case (name, value) => new Variable(name, value) }

    Logger("LambdaObject").debug(s"Calling $boundFn with (${args.withoutSelf}) in ${boundFn.scope}")

    val scope = boundFn.scope.newChild(positionalVariables ++ namedVariables)

    val result = c.interpreter.evaluate(
      Value.Operation(boundFn.fn.body, l),
      scope,
      c.callScope,
      c.runMode,
      c.callStack
    )

    result
  }, traits = boundFn.traits, methodId = boundFn.fn.objectId),

  "runTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(FunctionParams.Self)),
    { (_, args, l) =>
      val boundFn = args.getFunction(FunctionParams.Self)
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.CompileTime, FunctionTrait.Partial))

      Value.BoundFunction(
        BoundFunction(
          fn = boundFn.fn,
          scope = boundFn.scope,
          traits = traits
        ),
        l
      )
    }
  ),

  "compileTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(FunctionParams.Self)),
    { (_, args, l) =>
      val boundFn = args.getFunction(FunctionParams.Self)
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.Runtime, FunctionTrait.Partial))

      Value.BoundFunction(
        BoundFunction(
          fn = boundFn.fn,
          scope = boundFn.scope,
          traits = traits
        ),
        l
      )
    }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (_, _, l) => Value.Boolean(true, l) }
  )
))
