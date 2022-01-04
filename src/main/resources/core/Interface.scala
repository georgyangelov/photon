package photon.core

import photon.New.StandardMethod
import photon.{AnyType, ArgumentType, Arguments, Location, MethodType, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._
import photon.interpreter.CallContext

object InterfaceTypeType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    "from" -> new StandardMethod {
      override def methodType(argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "answer",
        arguments = Seq(
          ArgumentType("value", AnyType)
        ),
        returns = InterfaceTypeType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val self = args.getNativeSelf[InterfaceType]
        val obj = args.get(Parameter(1, "object"))

        PureValue.Native(
          InterfaceInstance(obj, self),
          location
        )
      }
    }
  )
}

class InterfaceType(private val methodTypes: Seq[MethodType]) extends New.TypeObject {
  override val typeObject = InterfaceTypeType

  // TODO: These methods should be able to be evaluated partially (generating dynamic dispatch code)
  override val instanceMethods = methodTypes.map(t => t.name -> new StandardMethod {
    override def methodType(_argTypes: Arguments[New.TypeObject]) = t

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val self = args.getNativeSelf[InterfaceInstance]

      context.callOrThrowError(self.obj, t.name, args, location)
    }
  }).toMap
}

case class InterfaceInstance(obj: RealValue, interfaceType: InterfaceType) extends New.NativeObject(interfaceType)
