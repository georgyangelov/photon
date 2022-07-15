package photon.base

import scala.reflect.ClassTag

sealed trait MethodSignature {
  def specialize(args: Arguments[Value]): CallSpec
}

object MethodSignature {
  def any(returnType: Value) = Any(returnType)
//  def of(args: Seq[(String, UPattern)], returnType: UValue) = Pattern(args, returnType)
  def of(args: Seq[(String, Value)], returnType: Value) = Specific(args, returnType)

  case class Specific(
    argTypes: Seq[(String, Value)],
    returnType: Value
  ) extends MethodSignature {
    def specialize(args: Arguments[Value]): CallSpec = ???
  }

//  case class Pattern(
//    argTypes: Seq[(String, UPattern)],
//    returnType: Value
//  ) extends MethodSignature {
//    def specialize(args: Arguments[Value]): CallSpec = ???
//  }

  case class Any(returnType: Value) extends MethodSignature {
    def specialize(args: Arguments[Value]): CallSpec =
      CallSpec(
        args,
        Seq.empty,
        ???
        // returnType.evaluated(EvalMode.CompileTimeOnly).assertType
      )
  }

}

case class CallSpec(
  args: Arguments[Value],
  bindings: Seq[(String, Value)],
  returnType: Type
) {
  def self: Value = ???

  def selfEval[T <: Value](implicit tag: ClassTag[T]): T = ???
  def getEval[T <: Value](name: String)(implicit tag: ClassTag[T]): T = ???

  def selfEvalInlined[T <: Value](implicit tag: ClassTag[T]): T = ???
  def getEvalInlined[T <: Value](name: String)(implicit tag: ClassTag[T]): T = ???
}