package photon.base

import photon.core._

import scala.reflect.ClassTag

sealed trait MethodSignature {
  def specialize(args: Arguments[Value]): CallSpec
}

object MethodSignature {
  def any(returnType: Type) = Any(returnType)
//  def of(args: Seq[(String, UPattern)], returnType: UValue) = Pattern(args, returnType)
  def of(args: Seq[(String, Type)], returnType: Type) = Specific(args, returnType)

  case class Specific(
    argTypes: Seq[(String, Type)],
    returnType: Type
  ) extends MethodSignature {
    def specialize(args: Arguments[Value]): CallSpec = {
      // TODO: Actual type-checking
      val bindings = args.matchWith(argTypes)
        .map { case (name, (value, expectedType)) => name -> value }

      CallSpec(args, bindings, returnType)
    }
  }

//  case class Pattern(
//    argTypes: Seq[(String, UPattern)],
//    returnType: Value
//  ) extends MethodSignature {
//    def specialize(args: Arguments[Value]): CallSpec = ???
//  }

  case class Any(returnType: Type) extends MethodSignature {
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
  def self = args.self

  def requireSelf[T <: Value](env: Environment)(implicit tag: ClassTag[T]): T = {
    val self = args.self.evaluate(env)

    requireType[T](env, "self", Some(self))(tag)
  }

  def require[T <: Value](env: Environment, name: String)(implicit tag: ClassTag[T]): T = {
    val value = bindings
      .find { case varName -> _ => varName == name }
      .map(_._2)
      .map(_.evaluate(env))

    requireType[T](env, name, value)(tag)
  }

  private def requireType[T <: Value](env: Environment, name: String, value: Option[Value])(implicit tag: ClassTag[T]): T = {
    value match {
      case Some(value: T) => value

      case Some(value) if value.isOperation =>
        env.evalMode match {
          case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $value even runtime", None)
          case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $value compile-time", None)
          case EvalMode.Partial |
               EvalMode.PartialRunTimeOnly |
               EvalMode.PartialPreferRunTime => throw DelayCall
        }

      case Some(value) => throw EvalError(s"Invalid value type $value for name $name, expected $tag", None)

      case None => throw EvalError(s"Cannot find binding $name", None)
    }
  }

  def requireObject[T <: Any](env: Environment, name: String)(implicit tag: ClassTag[T]): T =
    require[$Object](env, name).assert[T](tag)

  def requireSelfObject[T <: Any](env: Environment)(implicit tag: ClassTag[T]): T =
    requireSelf[$Object](env).assert[T](tag)
}