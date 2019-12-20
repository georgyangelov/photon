package photon.core

import photon.{EvalError, Scope, Value}
import photon.core.NativeObject._

object LambdaObject extends NativeObject(Map(
  "call" -> { (c, args, l) =>
    val lambda = args.getLambda(0)

    if (args.size != 1 + lambda.params.size) {
      throw EvalError("Wrong number of arguments for this lambda", l)
    }

    val scope = Scope(lambda.scope, lambda.params.zip(args.drop(1)).toMap)
    val evalBlock = c.interpreter.evaluate(Value.Operation(lambda.body, l), scope)

    evalBlock
  }
))
