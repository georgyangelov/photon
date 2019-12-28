package photon.core

import photon.Value

import photon.core.NativeValue._

object StringObject extends NativeObject(Map(
  "==" -> { (_, args, l) => Value.Boolean(args.getString(0) == args.getString(1), l) }
))
