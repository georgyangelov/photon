package photon.core

import photon.New.StandardMethod
import photon.{AnyType, ArgumentType, Arguments, Location, MethodType, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._
import photon.interpreter.CallContext

object InterfaceTypeType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    "from" -> new StandardMethod {
      override val name = "from"
      override val arguments = Seq(
        ArgumentType("object", AnyType)
      )
      override val returns = AnyType

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

class InterfaceType(_methodTypes: Seq[MethodType]) extends New.TypeObject {
  override val typeObject = InterfaceTypeType

  // TODO: These methods should be able to be evaluated partially (generating dynamic dispatch code)
  override val instanceMethods = methodTypes.map(t => t.name -> new StandardMethod {
    override val name = t.name
    override val arguments = t.argumentTypes
    override val returns = t.returnType

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val self = args.getNativeSelf[InterfaceInstance]

      context.callOrThrowError(self.obj, name, args, location)
    }
  }).toMap
}

case class InterfaceInstance(obj: RealValue, interfaceType: InterfaceType) extends New.NativeObject(interfaceType)