package photon.base

import photon.core._
import photon.core.objects.$AnyStatic
import photon.core.operations.$Call

import scala.reflect.ClassTag

sealed trait MethodSignature {
  // TODO: Instead of passing scope, pass Arguments[(Value, Type)]
  def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError]

  // TODO: This is a half-measure, I need MethodSignature to be
  //       able to expose the arguments to the outside, but this
  //       depends on support for varargs and patterns
  def withoutFirstArgumentCalled(name: String): MethodSignature
  def hasArgumentCalled(name: String): Boolean
}

object MethodSignature {
  def any(returnType: Type) = Any(returnType)
//  def of(args: Seq[(String, UPattern)], returnType: UValue) = Pattern(args, returnType)
  def of(args: Seq[(String, Type)], returnType: Type) = Specific(args, returnType)

  // TODO: Resolve $LazyType so that it is printable
  private def assignableTo(value: Value, to: Type, scope: Scope): Either[Value, TypeError] = {
    val fromType = value.typ(scope)
    val toMetaType = to.typ(scope)

    // TODO: Check if we're in CompileTimeOnly execution
    if (to.isSameAs($AnyStatic)) return Left(value)
    if (fromType.isSameAs(to)) return Left(value)

    val fromMethod = toMetaType.method("from")
      .getOrElse { return Right(TypeError(s"Cannot assign value of type $fromType to $to - no `from` method", value.location)) }

    fromMethod.signature.specialize(
      Arguments.positional(null, Seq(value)),
      scope
    ) match {
      case Left(_) =>
        Left($Call("from", Arguments.positional(to, Seq(value)), value.location))

      case Right(typeError) =>
        Right(TypeError(s"Cannot assign value of type $fromType to $to - ${typeError.message}", value.location))
    }
  }

  case class Specific(
    argTypes: Seq[(String, Type)],
    returnType: Type
  ) extends MethodSignature {
    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] = {
      val bindings = args.matchWith(argTypes)
        .map { case (name, (value, expectedType)) =>
          val convertedValue = assignableTo(value, expectedType, scope) match {
            case Left(value) => value
            case Right(reason) => return Right(reason)
          }

          name -> convertedValue
        }

      Left(CallSpec(args, bindings, returnType))
    }

    override def withoutFirstArgumentCalled(name: String): MethodSignature = {
      val argsWithoutName = Seq.newBuilder[(String, Type)]
      var foundArgument = false

      // We only want to drop the first argument named this way.
      // This is because we want to be able to define class methods that
      // have a different `self` argument
      argTypes.foreach { case (param, value) =>
        if (param == name && !foundArgument) {
          foundArgument = true
        } else {
          argsWithoutName.addOne(param -> value)
        }
      }

      Specific(argsWithoutName.result, returnType)
    }

    override def hasArgumentCalled(name: String): Boolean = argTypes.exists { case (argName, _) => argName == name }
  }

//  case class Pattern(
//    argTypes: Seq[(String, UPattern)],
//    returnType: Value
//  ) extends MethodSignature {
//    def specialize(args: Arguments[Value]): CallSpec = ???
//  }

  case class Any(returnType: Type) extends MethodSignature {
    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] =
      Left(CallSpec(
        args,
        Seq.empty,
        returnType
        // returnType.evaluated(EvalMode.CompileTimeOnly).assertType
      ))

    override def withoutFirstArgumentCalled(name: String): MethodSignature = this
    override def hasArgumentCalled(name: String): Boolean = true
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

  def requirePositional[T <: Value](env: Environment, index: Int)(implicit tag: ClassTag[T]): T = {
    val value = args.positional(index).evaluate(env)

    requireTypePositional[T](env, index, value)(tag)
  }

  private def requireTypePositional[T <: Value](env: Environment, index: Int, value: Value)(implicit tag: ClassTag[T]): T = {
    value match {
      case value: T => value

      case value if value.isOperation =>
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

  def requirePositionalObject[T <: Any](env: Environment, index: Int)(implicit tag: ClassTag[T]): T =
    requirePositional[$Object](env, index).assert[T](tag)

  def requireSelfObject[T <: Any](env: Environment)(implicit tag: ClassTag[T]): T =
    requireSelf[$Object](env).assert[T](tag)

  def requireSelfInlined[T <: Value](env: Environment)(implicit tag: ClassTag[T]): T = {
    val self = args.self
      .evaluate(env)
      .partialValue(env, followReferences = true)
      .value

    requireType[T](env, "self", Some(self))(tag)
  }

  def requireInlined[T <: Value](env: Environment, name: String)(implicit tag: ClassTag[T]): T = {
    val value = bindings
      .find { case varName -> _ => varName == name }
      .map(_._2)
      .map(_.evaluate(env))
      // TODO: Should I check for empty variables?
      .map(_.partialValue(env, followReferences = true))
      .map(_.value)

    requireType[T](env, name, value)(tag)
  }

  def requireInlinedObject[T <: Any](env: Environment, name: String)(implicit tag: ClassTag[T]): T =
    requireInlined[$Object](env, name).assert[T](tag)

  def requireSelfInlinedObject[T <: Any](env: Environment)(implicit tag: ClassTag[T]): T =
    requireSelfInlined[$Object](env).assert[T](tag)
}