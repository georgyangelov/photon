package photon.core

import photon.PureValue
import photon.core.Conversions._

object StringObjectParams {
  val EqualsLeft: Parameter = Parameter(0, "left")
  val EqualsRight: Parameter = Parameter(1, "right")
}

import StringObjectParams._

object StringObject extends NativeObject(Map(
  "==" -> ScalaMethod(
    MethodOptions(Seq(EqualsLeft, EqualsRight)),
    { (_, args, l) => PureValue.Boolean(args.getString(EqualsLeft) == args.getString(EqualsRight), l) }
  )
))
