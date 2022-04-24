package photon.base

import scala.util.control.ControlThrowable

trait Method {
  val signature: MethodSignature

  def call(args: MethodType, location: Option[Location]): EValue
}

sealed trait MethodCallThrowable extends ControlThrowable
case object CannotCallRunTimeMethodInCompileTimeMethod extends MethodCallThrowable
case object CannotCallCompileTimeMethodInRunTimeMethod extends MethodCallThrowable
case object DelayCall                                  extends MethodCallThrowable

abstract class CompileTimeOnlyMethod extends Method {
  protected def apply(args: MethodType, location: Option[Location]): EValue

  def call(args: MethodType, location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.Partial |
           EvalMode.PartialPreferRunTime =>
        apply(args, location)

      case EvalMode.RunTime |
           EvalMode.PartialRunTimeOnly =>
        throw CannotCallCompileTimeMethodInRunTimeMethod
    }
  }
}

abstract class DefaultMethod extends Method {
  protected def apply(args: MethodType, location: Option[Location]): EValue

  def call(args: MethodType, location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime |
           EvalMode.Partial =>
        apply(args, location)

      case EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class PreferRunTimeMethod extends Method {
  protected def apply(args: MethodType, location: Option[Location]): EValue

  def call(args: MethodType, location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime =>
        apply(args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class RunTimeOnlyMethod extends Method {
  protected def apply(args: MethodType, location: Option[Location]): EValue

  def call(args: MethodType, location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
      case EvalMode.RunTime => apply(args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}