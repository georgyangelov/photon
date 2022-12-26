package photon.base

import photon.core.objects.$Core
import photon.lib.ScalaExtensions._

sealed trait MethodSignature {
  def hasSelfArgument: Boolean
  def withoutSelfArgument: MethodSignature

  def instantiate(types: ArgumentsWithoutSelf[Type]): Either[MethodSignature.Concrete, TypeError]
  def assignableFrom(other: MethodSignature): Either[AssignableFromResult, TypeError]
}

case class AssignableFromResult(
//  matchResult: MatchResult,
//  conversions: Seq[(String, Value.Wrapper)],
//  returnTypeConversion: Value.Wrapper
)

object MethodSignature {
  def of(argTypes: Seq[(String, Type)], returnType: Type) = MethodSignature.Concrete(argTypes, returnType)

  case class Any(returnType: Type) extends MethodSignature {
    override def instantiate(types: ArgumentsWithoutSelf[Type]): Either[Concrete, TypeError] = {
      val argTypes =
        types.positional.zipWithIndex.map { case typ -> index => s"_$index" -> typ } ++
        types.named.toSeq

      Left(Concrete(argTypes, returnType))
    }

    override def withoutSelfArgument: MethodSignature = this
    override def hasSelfArgument: Boolean = true

    override def assignableFrom(other: MethodSignature): Either[AssignableFromResult, TypeError] = other match {
      case Any(otherReturnType) =>
        val returnTypeWrapper = $Core.isTypeAssignable(otherReturnType, returnType) match {
          case Left(wrapper) => wrapper
          case Right(reason) => return Right(reason)
        }

        Left(AssignableFromResult(
//          Seq.empty,
//          returnTypeWrapper
        ))

      case Concrete(otherArgTypes, otherReturnType) =>
        val returnTypeWrapper = $Core.isTypeAssignable(otherReturnType, returnType) match {
          case Left(wrapper) => wrapper
          case Right(reason) => return Right(reason)
        }

        Left(AssignableFromResult(
//          otherArgTypes.map { case name -> _ => name -> (value => value) },
//          returnTypeWrapper
        ))

      case Template(fnScope, argPatterns, returnType) => ???
    }
  }

  case class Concrete(argTypes: Seq[(String, Type)], returnType: Type) extends MethodSignature {
    override def instantiate(types: ArgumentsWithoutSelf[Type]): Either[Concrete, TypeError] = Left(this)

    override def hasSelfArgument: Boolean = argTypes.exists { case (argName, _) => argName == "self" }
    override def withoutSelfArgument: MethodSignature = {
      val argsWithoutName = Seq.newBuilder[(String, Type)]
      var foundArgument = false

      // We only want to drop the first argument named this way.
      // This is because we want to be able to define class methods that
      // have a different `self` argument
      argTypes.foreach { case (param, value) =>
        if (param == "self" && !foundArgument) {
          foundArgument = true
        } else {
          argsWithoutName.addOne(param -> value)
        }
      }

      Concrete(argsWithoutName.result, returnType)
    }

    def specialize(args: Arguments[(Value, Type)]): Either[CallSpec, TypeError] = {
      val bindings = args.withoutSelf.matchWith(argTypes)
        .map { case (name, ((value, valueType), expectedType)) =>
          val convertedValue = $Core.checkAndConvertTypes(value, valueType, expectedType) match {
            case Left(value) => value
            case Right(typeError) => return Right(typeError)
          }

          name -> convertedValue
        }

      Left(
        CallSpec(
          args.map(_._1),
          bindings,
          returnType
        )
      )
    }

    override def assignableFrom(other: MethodSignature): Either[AssignableFromResult, TypeError] = other match {
      case Any(otherReturnType) =>
        val convertedValue = $Core.isTypeAssignable(otherReturnType, returnType) match {
          case Left(value) => value
          case Right(typeError) => return Right(typeError)
        }

        Left(AssignableFromResult(
//          Seq.empty,
//          convertedValue
        ))

      case Concrete(otherArgTypes, otherReturnType) =>
        val returnTypesResult = $Core.isTypeAssignable(otherReturnType, returnType) match {
          case Left(value) => value
          case Right(typeError) => return Right(typeError)
        }

        val argumentTypesResult = argTypes.map(Some(_))
          .zipAll(otherArgTypes.map(Some(_)), None, None)
          .map {
            case (Some(aName -> aType), Some(bName -> bType)) =>
              if (aName != bName) {
                // TODO: Better location here
                return Right(TypeError("Different argument names", bType.location))
              } else {
                $Core.isTypeAssignable(bType, aType) match {
                  case Left(value) => value
                  case Right(typeError) => return Right(typeError)
                }
              }
            // TODO: Better location here
            case _ => return Right(TypeError("Invalid argument count", otherReturnType.location))
          }

        Left(AssignableFromResult())

      case template: Template =>
        // TODO: This is not correct because we need to preserve the order as well
        val otherConcrete = template.instantiate(ArgumentsWithoutSelf.named(argTypes.toMap)) match {
          case Left(value) => value
          case Right(typeError) => return Right(typeError)
        }

        $Core.isTypeAssignable(otherConcrete.returnType, returnType) match {
          case Left(value) => value
          case Right(typeError) => return Right(typeError)
        }

        Left(AssignableFromResult())
    }
  }

  case class Template(fnScope: Scope, argPatterns: Seq[(String, ValuePattern)], returnType: Value) extends MethodSignature {
    // TODO: Duplication with Concrete
    override def hasSelfArgument: Boolean = argPatterns.exists { case (argName, _) => argName == "self" }
    override def withoutSelfArgument: MethodSignature = {
      val argsWithoutName = Seq.newBuilder[(String, ValuePattern)]
      var foundArgument = false

      // We only want to drop the first argument named this way.
      // This is because we want to be able to define class methods that
      // have a different `self` argument
      argPatterns.foreach { case (param, value) =>
        if (param == "self" && !foundArgument) {
          foundArgument = true
        } else {
          argsWithoutName.addOne(param -> value)
        }
      }

      Template(fnScope, argsWithoutName.result, returnType)
    }

    override def instantiate(types: ArgumentsWithoutSelf[Type]): Either[MethodSignature.Concrete, TypeError] = {
      val (bindingScope, argTypes) = types.matchWith(argPatterns).mapWithRollingContext(fnScope) {
        case (fnScope, (name, (realType, pattern))) =>
          val env = Environment(fnScope, EvalMode.CompileTimeOnly)

          pattern match {
            case ValuePattern.Expected(expectedValue, location) =>
              val expectedType = expectedValue.evaluate(env).value.asType

              $Core.isTypeAssignable(realType, expectedType) match {
                case Left(value) => value
                case Right(typeError) => return Right(typeError)
              }

              fnScope -> (name -> expectedType)

            case pattern =>
              // TODO: Make `applyTo` provide a reason
              val matchResult = pattern.applyTo(realType, env) match {
                case Some(matchResult) => matchResult
                case None => return Right(TypeError("Type does not match pattern", realType.location))
              }

              val newFnScope = fnScope.newChild(matchResult.bindings.toSeq)

              newFnScope -> (name -> realType)
          }
      }

      val realReturnType = returnType.evaluate(Environment(bindingScope, EvalMode.CompileTimeOnly)).value.asType

      Left(Concrete(argTypes.toSeq, realReturnType))
    }

    override def assignableFrom(other: MethodSignature): Either[AssignableFromResult, TypeError] =
      Right(TypeError("Cannot assign any method to a template function signature", None))
  }
}
