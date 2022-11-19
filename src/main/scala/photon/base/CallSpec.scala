package photon.base

import photon.core._
import photon.core.objects.$Core
import photon.frontend.ValuePattern
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

    override def canBeAssignedFrom(moreGeneralSignature: MethodSignature): Boolean = moreGeneralSignature match {
      // TODO: Make the actual conversion, not just a check? Because of interfaces
      case Specific(otherArgTypes, otherReturnType) =>
        val argumentTypesAreCompatible = argPatterns.map(Some(_))
          .zipAll(otherArgTypes.map(Some(_)), None, None)
          .forall {
            case (Some(aName -> aPattern), Some(bName -> bType)) =>
              val namesMatch = aName == bName

              // TODO: Is there a case where we would be able to assign concrete-typed fn to pattern-typed fn?
              //       Seems like it can't work unless it's 100% the same
              val typesMatch = aPattern match {
                case ValuePattern.Expected(aTypeValue, location) =>
                  val aType = aTypeValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).asType

                  $Core.isTypeAssignable(bType, aType)

                case _ => false
              }

              namesMatch && typesMatch
          }

        if (!argumentTypesAreCompatible) {
          // This early-returns intentionally, because the returnType may be dependent on variables from the
          // type patterns, and the `returnType.evaluate` below will fail.
          return false
        }

        val realReturnType = returnType.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).asType

        $Core.isTypeAssignable(otherReturnType, realReturnType)

//        val returnTypesAreCompatible = $Core.isTypeAssignable(otherReturnType, returnType)
//
//        val argumentTypesAreCompatible = argTypes.map(Some(_))
//          .zipAll(otherArgTypes.map(Some(_)), None, None)
//          .forall {
//            case (Some(aName -> aType), Some(bName -> bType)) => aName == bName && $Core.isTypeAssignable(bType, aType)
//            case _ => false
//          }
//
//        returnTypesAreCompatible && argumentTypesAreCompatible

      // Can't assign pattern method to anything besides specific one
      case patterns: Patterns => false

//        patterns.canTypesBeUsed(
//          ArgumentsWithoutSelf.positional(argTypes.map(_._2)),
//          returnType
//        )

// TODO
//      case Any(otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType.evaluate(env))
//
//      case Patterns(fnScope, argPatterns, returnType) => ???
    }

    // TODO: Duplication with #specialize below
    def canTypesBeUsed(otherArgs: ArgumentsWithoutSelf[Type], otherReturnType: Type): Boolean = {
      val argScope = otherArgs.matchWith(argPatterns)
        .foldLeft(fnScope) { (fnScope, matchedParam) =>
          val (_, (valueType, pattern)) = matchedParam

          pattern match {
            case ValuePattern.Expected(expectedValue, _) =>
              val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).asType
              val isAssignable = $Core.isTypeAssignable(valueType, expectedType)

              if (!isAssignable) {
                return false
              }

              fnScope

            case pattern =>
              val matchResult = pattern.applyTo(valueType, Environment(fnScope, EvalMode.CompileTimeOnly)) match {
                case Some(value) => value
                case None => return false
              }

              val newFnScope = fnScope.newChild(matchResult.bindings.toSeq)

              newFnScope
          }
        }

      // TODO: Is this correct or is it the other way around?
      $Core.isTypeAssignable(
        returnType.evaluate(Environment(argScope, EvalMode.CompileTimeOnly)).asType,
        otherReturnType
      )
    }

    override def specialize(args: Arguments[Value], argScope: Scope): Either[CallSpec, TypeError] = {
      val (bindingScope, bindings) = args.matchWith(argPatterns)
        .mapWithRollingContext(fnScope) { (fnScope, matchedParam) =>
          val (name, (value, pattern)) = matchedParam
          val valueType = value.typ(argScope)

          pattern match {
            case ValuePattern.Expected(expectedValue, _) =>
              val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).asType
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

      val realReturnType = returnType.evaluate(Environment(bindingScope, EvalMode.CompileTimeOnly)).asType

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

  // TODO: Remove this once we have patterns
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