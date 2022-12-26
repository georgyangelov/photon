//package photon.base
//
//import photon.core._
//import photon.core.objects.$Core
//import photon.lib.ScalaExtensions._
//
//import scala.reflect.ClassTag
//
//sealed trait MethodSignature {
//  // TODO: Instead of passing scope, pass Arguments[(Value, Type)]
//  def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError]
//
//  // TODO: This is a half-measure, I need MethodSignature to be
//  //       able to expose the arguments to the outside, but this
//  //       depends on support for varargs and patterns
//  def withoutFirstArgumentCalled(name: String): MethodSignature
//  def hasArgumentCalled(name: String): Boolean
//
//  def canBeAssignedFrom(other: MethodSignature): Boolean = assignFrom(other).isDefined
//  def assignFrom(other: MethodSignature): Option[MatchResult]
//}
//
//object MethodSignature {
//  // TODO: Cleanup - don't need these factories
//  def any(returnType: Type) = Any(returnType)
//
//  def of(args: Seq[(String, Type)], returnType: Type) = Specific(args, returnType)
//
//  def ofPatterns(fnScope: Scope, args: Seq[(String, ValuePattern)], returnType: Value) =
//    Patterns(fnScope, args, returnType)
//
//  // TODO: Replace this with the Patterns case?
//  case class Specific(
//    argTypes: Seq[(String, Type)],
//    returnType: Type
//  ) extends MethodSignature {
//    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] = {
//      val bindings = args.withoutSelf.matchWith(argTypes)
//        .map { case (name, (value, expectedType)) =>
//          val valueType = value.typ(scope)
//          val convertedValue = $Core.checkAndConvertTypes(value, valueType, expectedType) match {
//            case Left(value) => value
//            case Right(typeError) => return Right(typeError)
//          }
//
//          name -> convertedValue
//        }
//
//      Left(CallSpec(args, bindings, returnType))
//    }
//
//    override def withoutFirstArgumentCalled(name: String): MethodSignature = {
//      val argsWithoutName = Seq.newBuilder[(String, Type)]
//      var foundArgument = false
//
//      // We only want to drop the first argument named this way.
//      // This is because we want to be able to define class methods that
//      // have a different `self` argument
//      argTypes.foreach { case (param, value) =>
//        if (param == name && !foundArgument) {
//          foundArgument = true
//        } else {
//          argsWithoutName.addOne(param -> value)
//        }
//      }
//
//      Specific(argsWithoutName.result, returnType)
//    }
//
//    override def hasArgumentCalled(name: String): Boolean = argTypes.exists { case (argName, _) => argName == name }
//
//    override def assignFrom(moreGeneralSignature: MethodSignature) = {
//      val assignableFromOther = moreGeneralSignature match {
//        // TODO: Make the actual conversion, not just a check? Because of interfaces
//        case Specific(otherArgTypes, otherReturnType) =>
//          val returnTypesAreCompatible = $Core.isTypeAssignable(otherReturnType, returnType)
//
//          val argumentTypesAreCompatible = argTypes.map(Some(_))
//            .zipAll(otherArgTypes.map(Some(_)), None, None)
//            .forall {
//              case (Some(aName -> aType), Some(bName -> bType)) => aName == bName && $Core.isTypeAssignable(bType, aType)
//              case _ => false
//            }
//
//          returnTypesAreCompatible && argumentTypesAreCompatible
//
//        case patterns: Patterns =>
//          patterns.canTypesBeUsed(
//            ArgumentsWithoutSelf.positional(argTypes.map(_._2)),
//            returnType
//          )
//
//        case Any(otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
//      }
//
//      if (assignableFromOther)
//        Some(MatchResult(Map.empty))
//      else
//        None
//    }
//  }
//
//  case class Any(returnType: Type) extends MethodSignature {
//    def specialize(args: Arguments[Value], scope: Scope): Either[CallSpec, TypeError] =
//      Left(CallSpec(
//        args,
//        Seq.empty,
//        returnType
//      ))
//
//    override def withoutFirstArgumentCalled(name: String): MethodSignature = this
//    override def hasArgumentCalled(name: String): Boolean = true
//
//    override def assignFrom(other: MethodSignature) = {
//      val assignableFromOther = other match {
//        // TODO: Make the actual conversion, not just a check
//        case Specific(argTypes, otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
//        case Any(otherReturnType) => $Core.isTypeAssignable(otherReturnType, returnType)
//        // TODO: Patterns
//      }
//
//      if (assignableFromOther)
//        Some(MatchResult(Map.empty))
//      else
//        None
//    }
//  }
//
//  case class Patterns(fnScope: Scope, argPatterns: Seq[(String, ValuePattern)], returnType: Value) extends MethodSignature {
//    override def hasArgumentCalled(name: String) = argPatterns.exists { case (argName, _) => argName == name }
//
//    // TODO: Duplication with the type above
//    override def withoutFirstArgumentCalled(name: String) = {
//      val argsWithoutName = Seq.newBuilder[(String, ValuePattern)]
//      var foundArgument = false
//
//      // We only want to drop the first argument named this way.
//      // This is because we want to be able to define class methods that
//      // have a different `self` argument
//      argPatterns.foreach { case (param, value) =>
//        if (param == name && !foundArgument) {
//          foundArgument = true
//        } else {
//          argsWithoutName.addOne(param -> value)
//        }
//      }
//
//      Patterns(fnScope, argsWithoutName.result, returnType)
//    }
//
//    override def assignFrom(other: MethodSignature) = other match {
//      // TODO: Should be able to do this in some cases, depends on the patterns
//      case Any(returnType) => None
//
//      // TODO: Should be able to do this as well, depends on the patterns
//      case Patterns(fnScope, argPatterns, returnType) => None
//
//      case Specific(argTypes, returnType) =>
//        val types = argTypes.zip(argPatterns)
//          .map { case ((givenName, givenType), (expectedName, expectedType)) =>
//            if (givenName != expectedName) {
//              return None
//            }
//
//            givenName -> (givenType -> expectedType)
//          }
//
//        specializeTypes1(types)
//    }
//
//    def specializeTypes(args: ArgumentsWithoutSelf[Type]): Option[Option[Type]] =
//      specializeTypes1(args.matchWith(argPatterns)).map { case (matchResult, returnType) => returnType }
//
//    // TODO: Duplication with #specialize?
//    private def specializeTypes1(types: Seq[(String, (Type, ValuePattern))]): Option[(MatchResult, Option[Type])] = {
//      val (argScope, matchResult) = types.foldLeft((fnScope, MatchResult.empty)) { case ((fnScope, matchResult), matchedParam) =>
//        val (_, (valueType, pattern)) = matchedParam
//
//        pattern match {
//          case ValuePattern.Expected(expectedValue, _) =>
//            val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).value.asType
//            val isAssignable = $Core.isTypeAssignable(valueType, expectedType)
//
//            if (!isAssignable) {
//              return None
//            }
//
//            (fnScope, matchResult)
//
//          case pattern =>
//            val newMatchResult = pattern.applyTo(valueType, Environment(fnScope, EvalMode.CompileTimeOnly)) match {
//              case Some(value) => value
//              case None => return None
//            }
//
//            val newFnScope = fnScope.newChild(newMatchResult.bindings.toSeq)
//
//            (newFnScope, matchResult + newMatchResult)
//        }
//      }
//
//      Some(
//        (
//          matchResult,
//          Some(returnType.evaluate(Environment(argScope, EvalMode.CompileTimeOnly)).value.asType)
//        )
//      )
//    }
//
//    // TODO: Duplication with #specialize below
//    def canTypesBeUsed(otherArgs: ArgumentsWithoutSelf[Type], otherReturnType: Type): Boolean = {
//      val types = otherArgs.matchWith(argPatterns)
//
//      specializeTypes(types) match {
//        case Some((_, Some(returnType))) =>
//          // TODO: This checks if it's assignable but doesn't actually convert it if needed
//          $Core.isTypeAssignable(returnType, otherReturnType)
//
//        // TODO: Change this when making template return types inferrable
//        case Some(_) => ???
//        case None => false
//      }
//    }
//
//    override def specialize(args: Arguments[Value], argScope: Scope): Either[CallSpec, TypeError] = {
//      val (bindingScope, bindings) = args.withoutSelf.matchWith(argPatterns)
//        .mapWithRollingContext(fnScope) { (fnScope, matchedParam) =>
//          val (name, (value, pattern)) = matchedParam
//          val valueType = value.typ(argScope)
//
//          pattern match {
//            case ValuePattern.Expected(expectedValue, _) =>
//              val expectedType = expectedValue.evaluate(Environment(fnScope, EvalMode.CompileTimeOnly)).value.asType
//              val convertedValue = $Core.checkAndConvertTypes(value, valueType, expectedType) match {
//                case Left(value) => value
//                case Right(typeError) => return Right(typeError)
//              }
//
//              fnScope -> (name -> convertedValue)
//
//            case pattern =>
//              val matchResult = pattern.applyTo(valueType, Environment(fnScope, EvalMode.CompileTimeOnly)) match {
//                case Some(value) => value
//                case None => return Right(TypeError("Value does not match pattern", value.location))
//              }
//
//              val newFnScope = fnScope.newChild(matchResult.bindings.toSeq)
//
//              newFnScope -> (name -> value)
//          }
//        }
//
//      val realReturnType = returnType.evaluate(Environment(bindingScope, EvalMode.CompileTimeOnly)).value.asType
//
//      Left(CallSpec(args, bindings.toSeq, realReturnType))
//    }
//  }
//}