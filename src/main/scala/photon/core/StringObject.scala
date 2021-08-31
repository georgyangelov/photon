package photon.core

import photon.{Arguments, Location, PureValue, RealValue}
import photon.core.Conversions._

object StringObjectParams {
  val EqualsLeft: Parameter = Parameter(0, "left")
  val EqualsRight: Parameter = Parameter(1, "right")
}

import StringObjectParams._

object StringObject extends NativeObject(Map(
  "==" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(args.getString(EqualsLeft) == args.getString(EqualsRight), location)
  }
))
