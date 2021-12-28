package photon.core

import photon.{AnyType, ArgumentType, Arguments, Location, MethodType, New, PureValue, RealValue, TypeType}
import photon.interpreter.{CallContext, EvalError}
import photon.core.Conversions._

// Optional.$type
object OptionalObjectType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    // Optional(_)
    // TODO: Results of this should get memoized so that the type references are the same
    "call" -> new New.CompileTimeOnlyMethod {
      override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "call",
        arguments = Seq(
          ArgumentType("innerType", TypeType)
        ),
        returns = AnyType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        PureValue.Native(OptionalType(args.getNative[New.TypeObject](Parameter(1, "innerType"))), location)
    }
  )
}
// Optional
object OptionalObject extends New.NativeObject(OptionalObjectType)

case class OptionalType(innerType: New.TypeObject) extends New.TypeObject {
  val self = this

  override val typeObject: New.TypeObject = new New.TypeObject {
    override val typeObject = TypeType
    override val instanceMethods = Map(
      "of" -> new New.StandardMethod {
        override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
          name = "of",
          arguments = Seq(ArgumentType("value", innerType)),
          returns = self
        )

        override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
          PureValue.Native(
            new OptionalInstance(self, Some(args.get(Parameter(1, "value")))),
            location
          )
        }
      },

      "none" -> new New.StandardMethod {
        override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
          name = "none",
          arguments = Seq.empty,
          returns = self
        )

        override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
          PureValue.Native(
            new OptionalInstance(self, None),
            location
          )
        }
      }
    )
  }

  override val instanceMethods = Map(
    "present?" -> new New.StandardMethod {
      override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "present?",
        arguments = Seq.empty,
        returns = BoolType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val optional = args.getNativeSelf[OptionalInstance]

        PureValue.Boolean(optional.value.isDefined, location)
      }
    },

    "get" -> new New.StandardMethod {
      override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "get",
        arguments = Seq.empty,
        returns = innerType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val optional = args.getNativeSelf[OptionalInstance]

        optional.value.getOrElse { throw EvalError("Tried to unwrap an empty optional", location) }
      }
    }
  )
}

class OptionalInstance(typeObject: OptionalType, val value: Option[RealValue]) extends New.NativeObject(typeObject)