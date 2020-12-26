package photon.core

import photon.Value

import photon.core.NativeValue._

object StringObjectParams {
  val EqualsLeft: Parameter = Parameter(0, "left")
  val EqualsRight: Parameter = Parameter(1, "right")
}

import StringObjectParams._

object StringObject extends NativeObject(Map(
  "==" -> ScalaMethod(
    MethodOptions(Seq(EqualsLeft, EqualsRight)),
    { (_, args, l) => Value.Boolean(args.getString(EqualsLeft) == args.getString(EqualsRight), l) }
  )
))
