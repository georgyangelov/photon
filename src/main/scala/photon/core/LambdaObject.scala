package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Scope, Value}
import photon.core.NativeValue._

object LambdaObject extends NativeObject(Map(
  "call" -> { (c, args, l) =>
    val lambda = args.getLambda(0)

    if (args.size != 1 + lambda.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    Logger("LambdaObject").debug(s"Calling $lambda with (${args.drop(1).mkString(", ")}) in ${lambda.scope}")

    val scope = Scope(lambda.scope, lambda.params.zip(args.drop(1)).toMap)
    val evalBlock = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope, partial = true)

    evalBlock
  }
))
