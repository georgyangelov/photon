package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Scope, Value}
import photon.core.NativeValue._

object LambdaObject extends NativeObject(Map(
  "call" -> ScalaMethod({ (c, args, l) =>
    val lambda = args.getLambda(0)

    if (args.size != 1 + lambda.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    Logger("LambdaObject").debug(s"Calling $lambda with (${args.drop(1).mkString(", ")}) in ${lambda.scope}")

    val scope = Scope(lambda.scope, lambda.params.zip(args.drop(1)).toMap)
    val result = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope, c.shouldTryToPartiallyEvaluate, c.isInPartialEvaluation)

    if (!c.shouldTryToPartiallyEvaluate && !result.isStatic) {
      throw EvalError(s"Cannot evaluate $lambda with (${args.drop(1).mkString(", ")}) in ${lambda.scope}", l)
    }

    result
  }),

  "to_bool" -> ScalaMethod({ (_, _, l) => Value.Boolean(true, l) })
))
