package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Lambda, LambdaTrait, Scope, Unparser, Value}
import photon.core.NativeValue._

object LambdaParams {
  val Self: Parameter = Parameter(0, "self")
}

case class LambdaObject(lambda: Lambda) extends NativeObject(Map(
  "call" -> ScalaVarargMethod({ (c, args, l) =>
    if (args.positional.size - 1 + args.named.size != lambda.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    Logger("LambdaObject").debug(s"Calling $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}")

    val lambdaParamNames = lambda.params.map { param => param.name }

    val positionalParams = lambdaParamNames.zip(args.positional.drop(1))
    val namesOfNamedParams = lambdaParamNames.drop(args.positional.size - 1).toSet

    val params = namesOfNamedParams.map { name =>
      args.named.get(name) match {
        case Some(value) => (name, value)
        case None => throw EvalError(s"Argument ${name} not specified in method call", l)
      }
    }

    val scope = Scope(lambda.scope, (positionalParams ++ params).toMap)

    val result = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope, c.mode)

    if (!c.mode.shouldTryToPartiallyEvaluate && !result.isStatic) {
      throw EvalError(s"Cannot evaluate $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}", l)
    }

    result
  }, traits = lambda.traits),

  "runTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(LambdaParams.Self)),
    { (_, args, l) =>
      val lambda = args.getLambda(LambdaParams.Self)

      Value.Lambda(
        Lambda(lambda.params, lambda.scope, lambda.body, traits = Set(LambdaTrait.Runtime)),
        l
      )
    }
  ),

  "compileTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(LambdaParams.Self)),
    { (_, args, l) =>
      val lambda = args.getLambda(LambdaParams.Self)

      Value.Lambda(
        Lambda(lambda.params, lambda.scope, lambda.body, traits = Set(LambdaTrait.CompileTime)),
        l
      )
    }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (_, _, l) => Value.Boolean(true, l) }
  )
))
