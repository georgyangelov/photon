package photon.core

import photon.Value
import photon.core.NativeValue._

object IntObject extends NativeObject(Map(
  "+" -> ScalaMethod({ (_, args, l) => Value.Int(args.getInt(0) + args.getInt(1), l) }),
  "-" -> ScalaMethod({ (_, args, l) => Value.Int(args.getInt(0) - args.getInt(1), l) }),
  "*" -> ScalaMethod({ (_, args, l) => Value.Int(args.getInt(0) * args.getInt(1), l) }),
  "/" -> ScalaMethod({ (_, args, l) => Value.Float(args.getDouble(0) / args.getDouble(1), l) }),

  "to_bool" -> ScalaMethod({ (_, _, l) => Value.Boolean(true, l) })
))
