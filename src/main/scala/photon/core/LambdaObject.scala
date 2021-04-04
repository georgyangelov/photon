package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalErrorOld, Lambda, LambdaTrait, Scope, Unparser, Value}
import photon.core.NativeValue._

object LambdaParams {
  val Self: Parameter = Parameter(0, "self")
}

case class LambdaObject(lambda: Lambda) extends NativeObject(Map(
  "call" -> ScalaVarargMethod({ (c, args, l) =>
    if (args.positional.size - 1 + args.named.size != lambda.params.size) {
      throw EvalErrorOld("Wrong number of arguments for this lambda", l)
    }

    Logger("LambdaObject").debug(s"Calling $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}")

    val lambdaParamNames = lambda.params.map { param => param.name }

    val positionalParams = lambdaParamNames.zip(args.positional.drop(1))
    val namesOfNamedParams = lambdaParamNames.drop(args.positional.size - 1).toSet

    val params = namesOfNamedParams.map { name =>
      args.named.get(name) match {
        case Some(value) => (name, value)
        case None => throw EvalErrorOld(s"Argument ${name} not specified in method call", l)
      }
    }

    val scope = Scope(lambda.scope, (positionalParams ++ params).toMap)

    val result = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope, c.runMode)

//    if (!c.mode.shouldTryToPartiallyEvaluate && !result.isStatic) {
//      throw EvalErrorOld(s"Cannot evaluate $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}", l)
//    }

    result
  }, traits = lambda.traits),

  "runTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(LambdaParams.Self)),
    { (_, args, l) =>
      val lambda = args.getLambda(LambdaParams.Self)
      val traits = lambda.traits.removedAll(Set(LambdaTrait.CompileTime, LambdaTrait.Partial))

      Value.Lambda(
        Lambda(lambda.params, lambda.scope, lambda.body, traits),
        l
      )
    }
  ),

  "compileTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(LambdaParams.Self)),
    { (_, args, l) =>
      val lambda = args.getLambda(LambdaParams.Self)
      val traits = lambda.traits.removedAll(Set(LambdaTrait.Runtime, LambdaTrait.Partial))

      Value.Lambda(
        Lambda(lambda.params, lambda.scope, lambda.body, traits),
        l
      )
    }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (_, _, l) => Value.Boolean(true, l) }
  )
))
