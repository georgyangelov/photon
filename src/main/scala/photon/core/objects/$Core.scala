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
      override val signature = MethodSignature.Any($AnyStatic)
      override def call(env: Environment, spec: CallSpec, location: Option[Location]) =
        typeCheck(env, spec, location).evaluate(env)
    }
  )

  def isTypeAssignable(from: Type, to: Type): Either[Value.Wrapper, TypeError] = {
    val fromType = from.resolvedType

    to.resolvedType match {
      case toType if toType == $AnyStatic => Left(value => value)
      case toType if toType == fromType => Left(value => value)
      case interface: Interface => interface.assignableFrom(fromType)
      case _ => Right(TypeError(s"Cannot assign type $from to $to", from.location))
    }
  }

  // This method should only be called compile-time
  def checkAndConvertTypes(value: Value, from: Type, to: Type): Either[Value, TypeError] = {
    isTypeAssignable(from, to) match {
      case Left(wrapper) => Left(wrapper(value))
      case Right(typeError) => Right(typeError)
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
