package photon.core

import com.typesafe.scalalogging.Logger
import photon.interpreter.EvalError
import photon.{ArgumentType, Arguments, FunctionTrait, Location, MethodType, New, RealValue, Value, Variable}
import photon.core.Conversions._

case class FunctionType(argumentTypes: Seq[ArgumentType], returnType: New.TypeObject) extends New.TypeObject {
  override val methods = Map.empty

  override val instanceMethods = Map(
    "call" -> new {} with NativeMethod {
      // TODO: Make traits be part of the type
      override val traits = Set(FunctionTrait.CompileTime, FunctionTrait.Runtime)

      override val methodType = MethodType("call", argumentTypes, returnType)

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val boundFn = args.getFunction(Parameter(0, "self"))

        if (args.positional.size + args.named.size != boundFn.fn.params.size) {
          throw EvalError("Not enough arguments passed for this lambda", location)
        }

        val lambdaParamNames = boundFn.fn.params.map(_.name)

        val positionalParams = lambdaParamNames.zip(args.positional)
        val namesOfNamedParams = lambdaParamNames.drop(args.positional.size).toSet

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
        val selfVariable = args.self.map { self => new Variable(boundFn.fn.selfName, Some(self)) }

        Logger("LambdaObject").debug(s"Calling $boundFn with (${args.withoutSelf}) in ${boundFn.scope}")

        val scope = boundFn.scope.newChild(positionalVariables ++ namedVariables ++ selfVariable.toSeq)

        val result = context.interpreter.evaluate(boundFn.fn.body, scope)

        result.realValue.getOrElse(result)
      }

      override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
    }
  )
}



//case class BoundFunctionObject(boundFn: BoundValue.Function) extends NativeObject(Map(
//  "call" -> new {} with NativeMethod {
//    override val traits = boundFn.traits
//    override val methodId = boundFn.fn.objectId
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      if (args.positional.size + args.named.size != boundFn.fn.params.size) {
//        throw EvalError("Not enough arguments passed for this lambda", location)
//      }
//
//      val lambdaParamNames = boundFn.fn.params.map(_.name)
//
//      val positionalParams = lambdaParamNames.zip(args.positional)
//      val namesOfNamedParams = lambdaParamNames.drop(args.positional.size).toSet
//
//      val namedParams = namesOfNamedParams.map { name =>
//        // TODO: This should use the name of the actual parameter always, it should not try to rename it.
//        //       This means parameters may have an "external" name and an "internal" name, probably something
//        //       like `sendEmail = (to as address, subject) { # This uses address and not to }; sendEmail(to = 'email@example.com')`
//        args.named.get(name.originalName) match {
//          case Some(value) => (name, value)
//          case None => throw EvalError(s"Argument $name not specified in method call", location)
//        }
//      }
//
//      val positionalVariables = positionalParams.map { case (name, value) => new Variable(name, Some(value)) }
//      val namedVariables = namedParams.map { case (name, value) => new Variable(name, Some(value)) }
//      val selfVariable = args.self.map { self => new Variable(boundFn.fn.selfName, Some(self)) }
//
//      Logger("LambdaObject").debug(s"Calling $boundFn with (${args.withoutSelf}) in ${boundFn.scope}")
//
//      val scope = boundFn.scope.newChild(positionalVariables ++ namedVariables ++ selfVariable.toSeq)
//
//      val result = context.interpreter.evaluate(boundFn.fn.body, scope)
//
//      result.realValue.getOrElse(result)
//    }
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
//  },
//
//  "runTimeOnly" -> new {} with NativeMethod {
//    // TODO: Partial as well
//    override val traits = Set(FunctionTrait.CompileTime)
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      val traits = boundFn.traits.removedAll(Set(FunctionTrait.CompileTime, FunctionTrait.Partial))
//
//      BoundValue.Function(
//        boundFn.fn,
//        traits,
//        boundFn.scope,
//        location
//      )
//    }
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
//  },
//
//  "compileTimeOnly" -> new {} with NativeMethod {
//    // TODO: Partial as well
//    override val traits = Set(FunctionTrait.CompileTime)
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      val traits = boundFn.traits.removedAll(Set(FunctionTrait.Runtime, FunctionTrait.Partial))
//
//      BoundValue.Function(
//        boundFn.fn,
//        traits,
//        boundFn.scope,
//        location
//      )
//    }
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
//  },
//
//  "toBool" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Boolean(true, location)
//  }
//))
