package photon.core

import photon.{ArgumentType, Arguments, Location, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._
import photon.interpreter.CallContext

object IntParams {
  val FirstParam: Parameter = Parameter(0, "first")
  val SecondParam: Parameter = Parameter(1, "second")
}

import IntParams._

object IntTypeType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map(
    "answer" -> new New.StandardMethod {
      override val name = "answer"
      override val arguments = Seq.empty
      override val returns = IntType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Int(42, location)
    }
  )
}

object IntType extends New.TypeObject {
  override val typeObject = IntTypeType

  override val instanceMethods = Map(
    "+" -> new New.StandardMethod {
      override val name = "+"
      override val arguments = Seq(
        ArgumentType("other", IntType)
      )
      override val returns = IntType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val a = args.getInt(FirstParam)
        val b = args.getInt(SecondParam)

        PureValue.Int(a + b, location)
      }
    },

    "-" -> new New.StandardMethod {
      override val name = "-"
      override val arguments = Seq(
        ArgumentType("other", IntType)
      )
      override val returns = IntType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val a = args.getInt(FirstParam)
        val b = args.getInt(SecondParam)

        PureValue.Int(a - b, location)
      }
    },

    "*" -> new New.StandardMethod {
      override val name = "*"
      override val arguments = Seq(
        ArgumentType("other", IntType)
      )
      override val returns = IntType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val a = args.getInt(FirstParam)
        val b = args.getInt(SecondParam)

        PureValue.Int(a * b, location)
      }
    },

//    "/" -> new New.StandardMethod {
//      override val name = "/"
//      override val arguments = Seq(
//        ArgumentType("other", FloatType)
//      )
//      override val returns = FloatType
//
//      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//        val a = args.getInt(FirstParam)
//        val b = args.getFloat(SecondParam)
//
//        PureValue.Float(a / b, location)
//      }
//    },

    "==" -> new New.StandardMethod {
      override val name = "=="
      override val arguments = Seq(
        ArgumentType("other", IntType)
      )
      override val returns = BoolType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val a = args.getInt(FirstParam)
        val b = args.getFloat(SecondParam)

        PureValue.Boolean(a == b, location)
      }
    },

    "toBool" -> new New.StandardMethod {
      override val name = "toBool"
      override val arguments = Seq.empty
      override val returns = BoolType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Boolean(value = true, location)
    }
  )
}
