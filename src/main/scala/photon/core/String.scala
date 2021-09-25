package photon.core

import photon.{ArgumentType, Arguments, Location, New, PureValue, RealValue}
import photon.core.Conversions._

object StringObjectParams {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")
}

import StringObjectParams._

object StringType extends New.TypeObject {
  override val methods = Map(
    "empty" -> new New.StandardMethod {
      override val name = "empty"
      override val arguments = Seq.empty
      override val returns = StringType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.String("", location)
    }
  )

  override val instanceMethods = Map(
    "==" -> new New.StandardMethod {
      override val name = "=="
      override val arguments = Seq(
        ArgumentType("other", StringType)
      )
      override val returns = BoolType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val self = args.getString(Self)
        val other = args.getString(Other)

        PureValue.Boolean(self == other, location)
      }
    }
  )
}
