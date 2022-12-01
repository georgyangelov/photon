package photon.core.objects

import photon.base._
import photon.core._

object $Core extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    // I can't use CompileTimeOnlyMethod here because that will fail in runtime-only functions + it gets the Environment
    // switched to CompileTimeOnly mode and I can't evaluate it in the original mode (since that's lost by the
    // CompileTimeOnlyMethod implementation)
    "typeCheck" -> new Method {
      override val signature = MethodSignature.any($AnyStatic)
      override def call(env: Environment, spec: CallSpec, location: Option[Location]) =
        typeCheck(env, spec, location).evaluate(env)
    }
  )

  def isTypeAssignable(from: Type, to: Type): Boolean = {
    val fromType = from.resolvedType

    to.resolvedType match {
      case toType if toType == $AnyStatic => true
      case toType if toType == fromType => true
      case interface: Interface => interface.canBeAssignedFrom(fromType)
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
          Left(InterfaceValue(value, interface, location))
        } else {
          Right(TypeError(s"Cannot assign type $fromType to interface $interface", value.location))
        }

      case toType => Right(TypeError(s"Cannot assign type $fromType to $toType", value.location))
    }
  }

  private def typeCheck(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
    val value = spec.args.positional.head
    val valueType = value.typ(env.scope)

    val requiredType = spec.args.positional(1)
      // The type must be known at compile-time
      // TODO: .evaluate(compileTime).value.asType => .evaluateType(scope)
      .evaluate(Environment(env.scope, EvalMode.CompileTimeOnly))
      .value
      .asType

    checkAndConvertTypes(value, valueType, requiredType) match {
      case Left(value) => value
      case Right(typeError) => throw typeError
    }
  }
}
