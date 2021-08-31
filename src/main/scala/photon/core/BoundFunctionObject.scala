package photon.core

import com.typesafe.scalalogging.Logger
import photon.interpreter.EvalError
import photon.{Arguments, BoundValue, FunctionTrait, Location, PureValue, RealValue, Value, Variable}
import photon.core.Conversions._

object FunctionParams {
  val Self: Parameter = Parameter(0, "self")
}

case class BoundFunctionObject(boundFn: BoundValue.Function) extends NativeObject(Map(
  "call" -> new {} with NativeMethod {
    override val traits = boundFn.traits
    override val methodId = boundFn.fn.objectId

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      if (args.positional.size - 1 + args.named.size != boundFn.fn.params.size) {
        throw EvalError("Wrong number of arguments for this lambda", location)
      }

      val lambdaParamNames = boundFn.fn.params.map(_.name)

      val positionalParams = lambdaParamNames.zip(args.positional.drop(1))
      val namesOfNamedParams = lambdaParamNames.drop(args.positional.size - 1).toSet

      val namedParams = namesOfNamedParams.map { name =>
        // TODO: This should use the name of the actual parameter always, it should not try to rename it.
        //       This means parameters may have an "external" name and an "internal" name, probably something
        //       like `sendEmail = (to as address, subject) { # This uses address and not to }; sendEmail(to = 'email@example.com')`
        args.named.get(name.originalName) match {
          case Some(value) => (name, value)
          case None => throw EvalError(s"Argument $name not specified in method call", location)
        }
      }

      val positionalVariables = positionalParams.map { case (name, value) => new Variable(name, Some(value)) }
      val namedVariables = namedParams.map { case (name, value) => new Variable(name, Some(value)) }

      Logger("LambdaObject").debug(s"Calling $boundFn with (${args.withoutSelf}) in ${boundFn.scope}")

      val scope = boundFn.scope.newChild(positionalVariables ++ namedVariables)

      val result = context.interpreter.evaluate(boundFn.fn.body, scope)

      result.realValue.getOrElse(result)
    }

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
  },

  "runTimeOnly" -> new {} with NativeMethod {
    // TODO: Partial as well
    override val traits = Set(FunctionTrait.CompileTime)

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.CompileTime, FunctionTrait.Partial))

      BoundValue.Function(
        boundFn.fn,
        traits,
        boundFn.scope,
        location
      )
    }

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
  },

  "compileTimeOnly" -> new {} with NativeMethod {
    // TODO: Partial as well
    override val traits = Set(FunctionTrait.CompileTime)

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.Runtime, FunctionTrait.Partial))

      BoundValue.Function(
        boundFn.fn,
        traits,
        boundFn.scope,
        location
      )
    }

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
  },

  "toBool" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Boolean(true, location)
  }
))
