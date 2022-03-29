package photon

import photon.core.Type

import scala.util.control.ControlThrowable
//import photon.lib.ObjectId

sealed abstract class MethodRunMode
object MethodRunMode {
  object CompileTimeOnly extends MethodRunMode
  object Default         extends MethodRunMode
  object PreferRunTime   extends MethodRunMode
  object RunTimeOnly     extends MethodRunMode
}

// TODO: Move to core.operations.Function
sealed abstract class InlinePreference
object InlinePreference {
  object ForceInline extends InlinePreference
  object Default     extends InlinePreference
  object NoInline    extends InlinePreference
}

sealed abstract class MethodCallThrowable extends ControlThrowable
case object CannotCallRunTimeMethodInCompileTimeMethod extends MethodCallThrowable
case object CannotCallCompileTimeMethodInRunTimeMethod extends MethodCallThrowable
case object DelayCall                                  extends MethodCallThrowable

case class MethodType(
  argTypes: Seq[(String, EValue)],
  returnType: Type
)
object MethodType {
  def of(args: Seq[(String, EValue)], returnType: Type) =
    MethodType(args, returnType)
}

trait Method /* extends Equals */ {
  //  val runMode: MethodRunMode
  //  val inlinePreference: InlinePreference = InlinePreference.Default

  def specialize(args: Arguments[EValue], location: Option[Location]): MethodType
  def call(args: Arguments[EValue], location: Option[Location]): EValue

  //  val objectId = ObjectId()
  //
  //  override def canEqual(that: Any): Boolean = that.isInstanceOf[UFunction]
  //  override def equals(that: Any): Boolean = {
  //    that match {
  //      case other: UFunction => this.objectId == other.objectId
  //      case _ => false
  //    }
  //  }
  //  override def hashCode(): Int = objectId.hashCode
}

abstract class CompileTimeOnlyMethod extends Method {
  protected def run(args: Arguments[EValue], location: Option[Location]): EValue

  def call(args: Arguments[EValue], location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.Partial |
           EvalMode.PartialPreferRunTime =>
        run(args, location)

      case EvalMode.RunTime |
           EvalMode.PartialRunTimeOnly =>
        throw CannotCallCompileTimeMethodInRunTimeMethod
    }
  }
}

abstract class DefaultMethod extends Method {
  protected def run(args: Arguments[EValue], location: Option[Location]): EValue

  def call(args: Arguments[EValue], location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime |
           EvalMode.Partial =>
        run(args, location)

      case EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class PreferRunTimeMethod extends Method {
  protected def run(args: Arguments[EValue], location: Option[Location]): EValue

  def call(args: Arguments[EValue], location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly |
           EvalMode.RunTime =>
        run(args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}

abstract class RunTimeOnlyMethod extends Method {
  protected def run(args: Arguments[EValue], location: Option[Location]): EValue

  def call(args: Arguments[EValue], location: Option[Location]): EValue = {
    EValue.context.evalMode match {
      case EvalMode.CompileTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
      case EvalMode.RunTime => run(args, location)

      case EvalMode.Partial |
           EvalMode.PartialPreferRunTime |
           EvalMode.PartialRunTimeOnly =>
        throw DelayCall
    }
  }
}