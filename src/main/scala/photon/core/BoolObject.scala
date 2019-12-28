package photon.core

import com.typesafe.scalalogging.Logger
import photon.Value
import photon.core.NativeValue._

object BoolObject extends NativeObject(Map(
  "!" -> { (_, args, l) => Value.Boolean(!args.getBool(0), l) },
  "not" -> { (_, args, l) => Value.Boolean(!args.getBool(0), l) },

  // TODO: Short-circuiting
  "and" -> { (_, args, l) => Value.Boolean(args.getBool(0) && args.getBool(1), l) },
  "or" -> { (_, args, l) => Value.Boolean(args.getBool(0) || args.getBool(1), l) },

  "to_bool" -> { (_, args, l) => Value.Boolean(args.getBool(0), l) },
  "if_else" -> { (c, args, l) =>
    val lambda = if (args.getBool(0)) {
      args.get(1)
    } else {
      args.get(2)
    }

    Logger("BoolObject").debug(s"Running $lambda, scope: ${lambda.asLambda.scope}")

    Core.nativeValueFor(lambda.asLambda).call(c, "call", Vector(lambda), l)
  }
))
