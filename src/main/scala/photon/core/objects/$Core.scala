package photon.core.objects

import photon.base._
import photon.core._

object $Core extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "typeCheck" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        typeCheck(env, spec, location)
      }
    }
  )

  def isTypeAssignable(from: Type, to: Type): Boolean = {
    val fromType = from.resolvedType

    to.resolvedType match {
      case toType if toType == $AnyStatic => true
      case toType if toType == fromType => true
      case interface: Interface => interface.canBeAssignedFrom(fromType)
//      case toType: $Function => signature.canBeAssignedFrom(fnType.signature)
      case _ => false
    }
  }

  // This method should only be called compile-time
  def checkAndConvertTypes(value: Value, from: Type, to: Type): Either[Value, TypeError] = {
    val fromType = from.resolvedType

    to.resolvedType match {
      case toType if toType == $AnyStatic => Left(value)
      case toType if toType == fromType => Left(value)

      case interface: Interface =>
        if (interface.canBeAssignedFrom(fromType)) {
          Left($Object(value, interface, location))
        } else {
          Right(TypeError(s"Cannot assign type $fromType to interface $interface", value.location))
        }

      case toType => Right(TypeError(s"Cannot assign type $fromType to $toType", value.location))
    }

//    if (toType == $AnyStatic) return Left(value)
//    if (fromType == toType) return Left(value)
//
//    if (toType.isInstanceOf[Interface]) {
//
//    }

//    val fromMethod = toMetaType.method("from")
//      .getOrElse { return Right(TypeError(s"Cannot assign value of type $fromType to $toType - no `from` method", value.location)) }
//
//    fromMethod.signature.specialize(
//      Arguments.positional(null, Seq(value)),
//      scope
//    ) match {
//      case Left(_) =>
//        Left($Call("from", Arguments.positional(to, Seq(value)), value.location))
//
//      case Right(typeError) =>
//        Right(TypeError(s"Cannot assign value of type $fromType to $to - ${typeError.message}", value.location))
//    }
  }

  private def typeCheck(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
    val value = spec.args.positional.head
    val valueType = value.typ(env.scope)

    // The type must be known at compile-time
    val requiredType = spec.args.positional(1).evaluate(env).asType

    checkAndConvertTypes(value, valueType, requiredType) match {
      case Left(value) => value
      case Right(typeError) => throw typeError
    }

//    val fromMethod = requiredType.typ(env.scope).method("from")
//      .getOrElse { throw EvalError(s"Cannot convert from $valueType to $requiredType because no `from` method exists", location) }

//    val partialEnv = Environment(
//      scope = env.scope,
//      evalMode = EvalMode.Partial
//    )

//    val fromMethod = requiredType.typ(env.scope).method("from")
//      .getOrElse { throw TypeError(s"Cannot convert from $valueType to $requiredType and no `from` method exists", location) }
//
//    fromMethod.signature.specialize(Arguments.positional(requiredType, Seq(value)), env.scope) match {
//      case Left(_) =>
//        $Call("from", Arguments.positional(requiredType, Seq(value)), location)
//          .evaluate(partialEnv)
//
//      case Right(typeError) => throw typeError
//    }
  }
}
