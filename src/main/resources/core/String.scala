package photon.core

import photon.{ArgumentType, Arguments, Location, MethodType, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._
import photon.interpreter.CallContext

object StringObjectParams {
  val Self = Parameter(0, "self")
  val Other = Parameter(1, "other")
}

import StringObjectParams._

object StringTypeType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    "empty" -> new New.StandardMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "empty",
        arguments = Seq.empty,
        returns = StringType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.String("", location)
    }
  )
}

object StringType extends New.TypeObject {
  override val typeObject = StringTypeType

  override val instanceMethods = Map(
    "==" -> new New.StandardMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "==",
        arguments = Seq(
          ArgumentType("other", StringType)
        ),
        returns = BoolType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val self = args.getString(Self)
        val other = args.getString(Other)

        PureValue.Boolean(self == other, location)
      }
    }
  )
}
