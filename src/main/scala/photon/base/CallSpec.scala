package photon.base

import photon.core._
import photon.core.objects.$Core
import photon.lib.ScalaExtensions._

import scala.reflect.ClassTag

sealed trait MethodSignature {
  // TODO: Instead of passing scope, pass Arguments[(Value, Type)]
  def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError]

  // TODO: This is a half-measure, I need MethodSignature to be
  //       able to expose the arguments to the outside, but this
  //       depends on support for varargs and patterns
  def withoutFirstArgumentCalled(name: String): MethodSignature
  def hasArgumentCalled(name: String): Boolean

  def canBeAssignedFrom(other: MethodSignature): Boolean
}

object MethodSignature {
  // TODO: Cleanup - don't need these factories
  def any(returnType: Type) = Any(returnType)

  def of(args: Seq[(String, Type)], returnType: Type) = Specific(args, returnType)

  def ofPatterns(fnScope: Scope, args: Seq[(String, ValuePattern)], returnType: Value) =
    Patterns(fnScope, args, returnType)

  // TODO: Replace this with the Patterns case?
  case class Specific(
    argTypes: Seq[(String, Type)],
    returnType: Type
  ) extends MethodSignature {
    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] = {
      val bindings = args.matchWith(argTypes)
        .map { case (name, (value, expectedType)) =>
          val valueType = value.typ(scope)
          val convertedValue = $Core.checkAndConvertTypes(value, valueType, expectedType) match {
            case Left(value) => value
            case Right(typeError) => return Right(typeError)
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

    override def canBeAssignedFrom(moreGeneralSignature: MethodSignature): Boolean = moreGeneralSignature match {
      // TODO: Make the actual conversion, not just a check? Because of interfaces
      case Specific(otherArgTypes, otherReturnType) =>
        val returnTypesAreCompatible = $Core.isTypeAssignable(otherReturnType, returnType)

        val argumentTypesAreCompatible = argTypes.map(Some(_))
          .zipAll(otherArgTypes.map(Some(_)), None, None)
          .forall {
            case (Some(aName -> aType), Some(bName -> bType)) => aName == bName && $Core.isTypeAssignable(bType, aType)
            case _ => false
          }

        returnTypesAreCompatible && argumentTypesAreCompatible

      case patterns: Patterns =>
        patterns.canTypesBeUsed(
          ArgumentsWithoutSelf.positional(argTypes.map(_._2)),
          returnType
        )

      case Any(otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
    }
  }

  case class Any(returnType: Type) extends MethodSignature {
    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] =
      Left(CallSpec(
        args,
        Seq.empty,
        returnType
      ))

    override def withoutFirstArgumentCalled(name: String): MethodSignature = this
    override def hasArgumentCalled(name: String): Boolean = true

    override def canBeAssignedFrom(other: MethodSignature): Boolean = other match {
      // TODO: Make the actual conversion, not just a check
      case Specific(argTypes, otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
      case Any(otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
      // TODO: Patterns
    }
  }

  case class Patterns(fnScope: Scope, argPatterns: Seq[(String, ValuePattern)], returnType: Value) extends MethodSignature {
    override def hasArgumentCalled(name: String) = argPatterns.exists { case (argName, _) => argName == name }

    // TODO: Duplication with the type above
    override def withoutFirstArgumentCalled(name: String) = {
      val argsWithoutName = Seq.newBuilder[(String, ValuePattern)]
      var foundArgument = false

      // We only want to drop the first argument named this way.
      // This is because we want to be able to define class methods that
      // have a different `self` argument
      argPatterns.foreach { case (param, value) =>
        if (param == name && !foundArgument) {
          foundArgument = true
        } else {
          argsWithoutName.addOne(param -> value)
        }
      }

      Patterns(fnScope, argsWithoutName.result, returnType)
    }

    // Can't assign to pattern types at all
    override def canBeAssignedFrom(other: MethodSignature) = false

    def specializeTypes(argTypes: ArgumentsWithoutSelf[Type]): Option[Option[Type]] = {
      val argScope = argTypes.matchWith(argPatterns)
        .foldLeft(fnScope) { (fnScope, matchedParam) =>
          val (_, (valueType, pattern)) = matchedParam

          pattern match {
            case ValuePattern.Expected(expectedValue, _) =>
              val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).value.asType
              val isAssignable = $Core.isTypeAssignable(valueType, expectedType)

              if (!isAssignable) {
                return None
              }

              fnScope

            case pattern =>
              val matchResult = pattern.applyTo(valueType, Environment(fnScope, EvalMode.CompileTimeOnly)) match {
                case Some(value) => value
                case None => return None
              }

              val newFnScope = fnScope.newChild(matchResult.bindings.toSeq)

              newFnScope
          }
        }

      Some(Some(returnType.evaluate(Environment(argScope, EvalMode.CompileTimeOnly)).value.asType))
    }

    // TODO: Duplication with #specialize below
    def canTypesBeUsed(otherArgs: ArgumentsWithoutSelf[Type], otherReturnType: Type): Boolean = {
      specializeTypes(otherArgs) match {
        case Some(Some(returnType)) =>
          // TODO: This checks if it's assignable but doesn't actually convert it if needed
          $Core.isTypeAssignable(returnType, otherReturnType)

        // TODO: Change this when making template return types inferrable
        case Some(_) => ???
        case None => false
      }
    }

    override def specialize(args: Arguments[Value], argScope: Scope): Either[CallSpec, TypeError] = {
      val (bindingScope, bindings) = args.matchWith(argPatterns)
        .mapWithRollingContext(fnScope) { (fnScope, matchedParam) =>
          val (name, (value, pattern)) = matchedParam
          val valueType = value.typ(argScope)

          pattern match {
            case ValuePattern.Expected(expectedValue, _) =>
              val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).value.asType
              val convertedValue = $Core.checkAndConvertTypes(value, valueType, expectedType) match {
                case Left(value) => value
                case Right(typeError) => return Right(typeError)
              }

              fnScope -> (name -> convertedValue)

            case pattern =>
              val matchResult = pattern.applyTo(valueType, Environment(fnScope, EvalMode.CompileTimeOnly)) match {
                case Some(value) => value
                case None => return Right(TypeError("Value does not match pattern", value.location))
              }

              val newFnScope = fnScope.newChild(matchResult.bindings.toSeq)

              newFnScope -> (name -> value)
          }
        }

      val realReturnType = returnType.evaluate(Environment(bindingScope, EvalMode.CompileTimeOnly)).value.asType

      Left(CallSpec(args, bindings.toSeq, realReturnType))
    }
  }
}

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