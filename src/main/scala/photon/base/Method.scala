package photon.base

import scala.util.control.ControlThrowable

trait Method {
  val signature: MethodSignature

  def call(env: Environment, args: CallSpec, location: Option[Location]): Value
}

sealed trait MethodCallThrowable extends ControlThrowable
case object CannotCallRunTimeMethodInCompileTimeMethod extends MethodCallThrowable
case object CannotCallCompileTimeMethodInRunTimeMethod extends MethodCallThrowable
case object DelayCall                                  extends MethodCallThrowable

abstract class CompileTimeOnlyMethod extends Method {
  protected def apply(env: Environment, args: CallSpec, location: Option[Location]): Value

  def call(env: Environment, args: CallSpec, location: Option[Location]): Value = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.Partial |
           EvalMode.PartialPreferRunTime =>
        apply(env, args, location)

      case EvalMode.RunTime |
           EvalMode.PartialRunTimeOnly =>
        throw CannotCallCompileTimeMethodInRunTimeMethod
    }
  }
}

abstract class DefaultMethod extends Method {
  protected def apply(env: Environment, args: CallSpec, location: Option[Location]): Value

  def call(env: Environment, args: CallSpec, location: Option[Location]): Value = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime |
           EvalMode.Partial =>
        apply(env, args, location)

      case EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class PreferRunTimeMethod extends Method {
  protected def apply(env: Environment, args: CallSpec, location: Option[Location]): Value

  def call(env: Environment, args: CallSpec, location: Option[Location]): Value = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime =>
        apply(env, args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class RunTimeOnlyMethod extends Method {
  protected def apply(env: Environment, args: CallSpec, location: Option[Location]): Value

  def call(env: Environment, args: CallSpec, location: Option[Location]): Value = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
      case EvalMode.RunTime => apply(env, args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}