package photon.base

import scala.reflect.ClassTag

object MethodSignature {
  def of(args: Seq[(String, Pattern)], returnType: EValue) = MethodSignature(args, returnType)
}

case class MethodSignature(
  argTypes: Seq[(String, Pattern)],
  returnType: EValue
) {
  def specialize(args: Arguments[EValue]): CallSpec = ???
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