package photon.base

import photon.core.$Pattern

import scala.reflect.ClassTag

object MethodSignature {
  def any(returnType: EValue) = AnyMethodSignature(returnType)
  def of(args: Seq[(String, $Pattern.Value)], returnType: EValue) = SpecificMethodSignature(args, returnType)
}

sealed trait MethodSignature {
  def specialize(args: Arguments[EValue]): CallSpec
}

case class SpecificMethodSignature(
  argTypes: Seq[(String, EValue)],
  returnType: EValue
) extends MethodSignature {
  def specialize(args: Arguments[EValue]): CallSpec = {

  }
}

case class AnyMethodSignature(returnType: EValue) extends MethodSignature {
  def specialize(args: Arguments[EValue]): CallSpec =
    CallSpec(
      args,
      Seq.empty,
      returnType.evaluated(EvalMode.CompileTimeOnly).assertType
    )
}

case class CallSpec(
  args: Arguments[EValue],
  bindings: Seq[(String, EValue)],
//  argTypes: Seq[(String, Type)],
  returnType: Type
) {
  def self: EValue = ???

  def selfEval[T <: EValue](implicit tag: ClassTag[T]): T = ???
  def getEval[T <: EValue](name: String)(implicit tag: ClassTag[T]): T = ???

  def selfEvalInlined[T <: EValue](implicit tag: ClassTag[T]): T = ???
  def getEvalInlined[T <: EValue](name: String)(implicit tag: ClassTag[T]): T = ???
}