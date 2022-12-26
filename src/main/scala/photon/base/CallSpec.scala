package photon.base

import photon.core.$Object

import scala.reflect.ClassTag

case class CallSpec(
  args: Arguments[Value],
  bindings: Seq[(String, Value)],
  returnType: Type
) {
  def self = args.self

  def requireSelf[T <: Value](env: Environment)(implicit tag: ClassTag[T]): EvalResult[T] = {
    val self = args.self.evaluate(env)

    requireType[T](env, "self", Some(self))(tag)
  }

  def require[T <: Value](env: Environment, name: String)(implicit tag: ClassTag[T]): EvalResult[T] = {
    val value = bindings
      .find { case varName -> _ => varName == name }
      .map(_._2)
      .map(_.evaluate(env))

    requireType[T](env, name, value)(tag)
  }

  def requirePositional[T <: Value](env: Environment, index: Int)(implicit tag: ClassTag[T]): EvalResult[T] = {
    val value = args.positional(index).evaluate(env)

    requireTypePositional[T](env, index, value)(tag)
  }

  def requireAllConcretePositional(env: Environment): EvalResult[Seq[Value]] = {
    val results = args.positional.map(_.evaluate(env))
    val values = results.map(_.value)

    values.find(_.isOperation) match {
      case Some(value) =>
        env.evalMode match {
          case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $value even runtime", None)
          case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $value compile-time", None)
          case EvalMode.Partial |
               EvalMode.PartialRunTimeOnly |
               EvalMode.PartialPreferRunTime => throw DelayCall
        }

      case None =>
    }

    val closures = results.flatMap(_.closures)

    EvalResult(values, closures)
  }

  // TODO: Remove this once we have patterns
  private def requireTypePositional[T <: Value](env: Environment, index: Int, value: EvalResult[Value])(implicit tag: ClassTag[T]): EvalResult[T] = {
    value match {
      case EvalResult(value: T, closures) => EvalResult(value, closures)

      case EvalResult(value, _) if value.isOperation =>
        env.evalMode match {
          case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $value even runtime", None)
          case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $value compile-time", None)
          case EvalMode.Partial |
               EvalMode.PartialRunTimeOnly |
               EvalMode.PartialPreferRunTime => throw DelayCall
        }

      case value => throw EvalError(s"Invalid value type $value for position $index, expected $tag", None)
    }
  }

  private def requireType[T <: Value](env: Environment, name: String, value: Option[EvalResult[Value]])(implicit tag: ClassTag[T]): EvalResult[T] = {
    value match {
      case Some(EvalResult(value: T, closures)) => EvalResult(value, closures)

      case Some(EvalResult(value, _)) if value.isOperation =>
        env.evalMode match {
          case EvalMode.RunTime => throw EvalError(s"Cannot evaluate $value even runtime", None)
          case EvalMode.CompileTimeOnly => throw EvalError(s"Cannot evaluate $value compile-time", None)
          case EvalMode.Partial |
               EvalMode.PartialRunTimeOnly |
               EvalMode.PartialPreferRunTime => throw DelayCall
        }

      case Some(EvalResult(value, _)) => throw EvalError(s"Invalid value type $value for name $name, expected $tag", None)

      case None => throw EvalError(s"Cannot find binding $name", None)
    }
  }

  def requireObject[T <: Any](env: Environment, name: String)(implicit tag: ClassTag[T]): EvalResult[T] =
    require[$Object](env, name).mapValue(_.assert[T](tag))

  def requirePositionalObject[T <: Any](env: Environment, index: Int)(implicit tag: ClassTag[T]): EvalResult[T] =
    requirePositional[$Object](env, index).mapValue(_.assert[T](tag))

  def requireSelfObject[T <: Any](env: Environment)(implicit tag: ClassTag[T]): EvalResult[T] =
    requireSelf[$Object](env).mapValue(_.assert[T](tag))

  def requireSelfInlined[T <: Value](env: Environment)(implicit tag: ClassTag[T]): EvalResult[T] = {
    val self = args.self
      // TODO: This is NOT correct in some way or the other
      .evaluate(env)
      .mapValue(_.partialValue(env, followReferences = true).value)

    // TODO: It's probably not correct to use partialValue without passing `closures` through it
    requireType[T](env, "self", Some(self))(tag)
  }

  def requireInlined[T <: Value](env: Environment, name: String)(implicit tag: ClassTag[T]): EvalResult[T] = {
    val value = bindings
      .find { case varName -> _ => varName == name }
      .map(_._2)
      .map(_.evaluate(env))
      // TODO: This is NOT correct in some way or the other
      // TODO: Should I check for empty variables?
      .map(_.mapValue(_.partialValue(env, followReferences = true).value))

    requireType[T](env, name, value)(tag)
  }

  def requireInlinedObject[T <: Any](env: Environment, name: String)(implicit tag: ClassTag[T]): EvalResult[T] =
    requireInlined[$Object](env, name).mapValue(_.assert[T](tag))

  def requireSelfInlinedObject[T <: Any](env: Environment)(implicit tag: ClassTag[T]): EvalResult[T] =
    requireSelfInlined[$Object](env).mapValue(_.assert[T](tag))
}