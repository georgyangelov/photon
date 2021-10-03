package photon

import photon.core.{NativeMethod, NativeValue}
import photon.interpreter.CallContext

//case class FnType(argumentTypes: Seq[ArgumentType], returnType: New.TypeObject) extends New.TypeObject {
//  // TODO: This is not true
//  override val methods = Map.empty
//  override val instanceMethods = Map.empty
//}

case class ArgumentType(name: String, typeValue: New.TypeObject)

case class MethodType(name: String, argumentTypes: Seq[ArgumentType], returnType: New.TypeObject)

//sealed abstract class TypeParam
//object TypeParam {
//  case class TypeObject(obj: New.TypeObject) extends TypeParam
//  class TypeVar extends TypeParam with CompareByObjectId
//  case class Union(alternatives: Seq[TypeParam]) extends TypeParam
//}

//object TypeParamConversions {
//  implicit class TypeParamConversion(typeObject: New.TypeObject) {
//
//  }
//}

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

//case class UnionType(alternatives: Seq[New.TypeObject]) extends New.TypeObject {
//  // TODO: This is not true
//  override val methods = Map.empty
//  override val instanceMethods = Map.empty
//}

object New {
  abstract class TypeObject extends NativeValue {
    val instanceMethods: Map[String, NativeMethod]

    // TODO: Do we need this since we have `instanceMethods`?
    lazy val methodTypes: Seq[MethodType] = instanceMethods.values.map(_.methodType).toSeq

    def instanceMethod(name: String): Option[NativeMethod] = instanceMethods.get(name)

    val toValue = PureValue.Native(this, None)
  }

  // TODO: Do I need this since the NativeValue doesn't have any more stuff?
  abstract class NativeObject(override val typeObject: TypeObject) extends NativeValue

  abstract class StandardMethod extends NativeMethod {
    final override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime)
    final override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
      throw new NotImplementedError("partialCall invoked on a method that is not marked as Partial")

    val name: String
    val arguments: Seq[ArgumentType]
//    val returns: TypeParam
    val returns: New.TypeObject

    // TODO: Make this non-lazy
    lazy val methodType = MethodType(name, arguments, returns)
//    def methodType(argTypes: Seq[ArgumentType]) = MethodType(name, arguments, returns)
  }

  abstract class CompileTimeOnlyMethod extends NativeMethod {
    final override val traits = Set(FunctionTrait.CompileTime)
    final override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
      throw new NotImplementedError("partialCall invoked on a method that is not marked as Partial")

    val name: String
    val arguments: Seq[ArgumentType]
    val returns: New.TypeObject

    // TODO: Make this non-lazy
    lazy val methodType = MethodType(name, arguments, returns)
  }
}