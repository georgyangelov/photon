package photon.core

import com.typesafe.scalalogging.Logger
import photon.{EvalError, Value}
import photon.core.NativeValue._

object BoolObject extends NativeObject(Map(
  "!" -> ScalaMethod({ (_, args, l) => Value.Boolean(!args.getBool(0), l) }),
  "not" -> ScalaMethod({ (_, args, l) => Value.Boolean(!args.getBool(0), l) }),

  // TODO: Short-circuiting
  "and" -> ScalaMethod({ (_, args, l) => Value.Boolean(args.getBool(0) && args.getBool(1), l) }),
  "or" -> ScalaMethod({ (_, args, l) => Value.Boolean(args.getBool(0) || args.getBool(1), l) }),

  "to_bool" -> ScalaMethod({ (_, args, l) => Value.Boolean(args.getBool(0), l) }),
  "if_else" -> ScalaMethod({ (c, args, l) =>
    val lambda = if (args.getBool(0)) {
      args.get(1)
    } else {
      args.get(2)
    }

    Logger("BoolObject").debug(s"Running $lambda, scope: ${lambda.asLambda.scope}")

    Core.nativeValueFor(lambda.asLambda).callOrThrowError(c, "call", Vector(lambda), l)
  })
))
