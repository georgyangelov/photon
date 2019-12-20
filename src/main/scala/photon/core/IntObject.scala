package photon.core

import photon.Value
import photon.core.NativeObject._

object IntObject extends NativeObject(Map(
  "+" -> { (_, args, l) => Value.Int(args.getInt(0) + args.getInt(1), l) },
  "-" -> { (_, args, l) => Value.Int(args.getInt(0) - args.getInt(1), l) },
  "*" -> { (_, args, l) => Value.Int(args.getInt(0) * args.getInt(1), l) },
  "/" -> { (_, args, l) => Value.Float(args.getDouble(0) / args.getDouble(1), l) }
))
