package photon.base

import scala.util.control.ControlThrowable

trait Method {
  val signature: MethodSignature

  def call(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value]
}

sealed trait MethodCallThrowable extends ControlThrowable
case object CannotCallRunTimeMethodInCompileTimeMethod extends MethodCallThrowable
case object CannotCallCompileTimeMethodInRunTimeMethod extends MethodCallThrowable
case object DelayCall                                  extends MethodCallThrowable

abstract class CompileTimeOnlyMethod extends Method {
  protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value]

  def call(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value] = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.Partial |
           EvalMode.PartialPreferRunTime =>
        apply(Environment(env.scope, EvalMode.CompileTimeOnly), spec, location)

      case EvalMode.RunTime |
           EvalMode.PartialRunTimeOnly =>
        throw CannotCallCompileTimeMethodInRunTimeMethod
    }
  }
}

abstract class DefaultMethod extends Method {
  protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value]

  def call(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value] = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime |
           EvalMode.Partial =>
        apply(env, spec, location)

      case EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class PreferRunTimeMethod extends Method {
  protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value]

  def call(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value] = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime =>
        apply(env, spec, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class RunTimeOnlyMethod extends Method {
  protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value]

  def call(env: Environment, spec: CallSpec, location: Option[Location]): EvalResult[Value] = {
    env.evalMode match {
      case EvalMode.CompileTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
      case EvalMode.RunTime => apply(env, spec, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}