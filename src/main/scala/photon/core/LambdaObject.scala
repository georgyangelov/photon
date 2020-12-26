package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Lambda, Scope, Unparser, Value}
import photon.core.NativeValue._

case class LambdaObject(lambda: Lambda) extends NativeObject(Map(
  "call" -> ScalaVarargMethod({ (c, args, l) =>
    if (args.positional.size - 1 + args.named.size != lambda.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    Logger("LambdaObject").debug(s"Calling $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}")

    val positionalParams = lambda.params.zip(args.positional.drop(1))
    val namesOfNamedParams = lambda.params.drop(args.positional.size - 1).toSet

    val params = namesOfNamedParams.map { name =>
      args.named.get(name) match {
        case Some(value) => (name, value)
        case None => throw EvalError(s"Argument ${name} not specified in method call", l)
      }
    }

    val scope = Scope(lambda.scope, (positionalParams ++ params).toMap)

    val result = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope, c.shouldTryToPartiallyEvaluate, c.isInPartialEvaluation)

    if (!c.shouldTryToPartiallyEvaluate && !result.isStatic) {
      throw EvalError(s"Cannot evaluate $lambda with (${Unparser.unparse(args)}) in ${lambda.scope}", l)
    }

    result
  }),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq()),
    { (_, _, l) => Value.Boolean(true, l) }
  )
))
