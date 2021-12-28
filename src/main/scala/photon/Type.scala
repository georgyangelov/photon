package photon

import photon.core.{NativeMethod, NativeValue}
import photon.interpreter.CallContext

case class ArgumentType(name: String, typeValue: New.TypeObject)

case class MethodType(name: String, arguments: Seq[ArgumentType], returns: New.TypeObject)

object TypeType extends New.TypeObject {
  override lazy val typeObject = TypeType
  override val instanceMethods = Map.empty
}

object AnyType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}

object UnknownType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}

object New {
  abstract class TypeObject extends NativeValue {
    val instanceMethods: Map[String, NativeMethod]

    def instanceMethod(name: String): Option[NativeMethod] = instanceMethods.get(name)

    val toValue = PureValue.Native(this, None)
  }

  // TODO: Do I need this since the NativeValue doesn't have any more stuff?
  abstract class NativeObject(override val typeObject: TypeObject) extends NativeValue

  abstract class StandardMethod extends NativeMethod {
    final override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime)
    final override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
      throw new NotImplementedError("partialCall invoked on a method that is not marked as Partial")

    def methodType(argTypes: Arguments[New.TypeObject]): MethodType
  }

  abstract class CompileTimeOnlyMethod extends NativeMethod {
    final override val traits = Set(FunctionTrait.CompileTime)
    final override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
      throw new NotImplementedError("partialCall invoked on a method that is not marked as Partial")

    def methodType(argTypes: Arguments[New.TypeObject]): MethodType
  }

  abstract class PartialMethod extends NativeMethod {
    final override val traits = Set(FunctionTrait.Partial)
    final override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      throw new NotImplementedError("call invoked on a partial method")

    def methodType(argTypes: Arguments[New.TypeObject]): MethodType
  }
}
