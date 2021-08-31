package photon.core

import photon.{Arguments, Location, PureValue, RealValue}
import photon.core.Conversions._

object IntParams {
  val FirstParam: Parameter = Parameter(0, "first")
  val SecondParam: Parameter = Parameter(1, "second")
}

import IntParams._

object IntObject extends NativeObject(Map(
  "+" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Int(args.getInt(FirstParam) + args.getInt(SecondParam), location)
  },

  "-" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Int(args.getInt(FirstParam) - args.getInt(SecondParam), location)
  },

  "*" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Int(args.getInt(FirstParam) * args.getInt(SecondParam), location)
  },

  "/" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Float(args.getDouble(FirstParam) / args.getDouble(SecondParam), location)
  },

  "==" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(args.getInt(FirstParam) == args.getInt(SecondParam), location)
  },

  "toBool" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(true, location)
  }
))
