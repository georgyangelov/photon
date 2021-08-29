package photon.core

import com.typesafe.scalalogging.Logger
import photon.core.BoundFunctionObject.wrapInLets
import photon.interpreter.EvalError
import photon.{BoundFunction, FunctionTrait, Location, Operation, RealValue, Value, Variable}

object FunctionParams {
  val Self: Parameter = Parameter(0, "self")
}

object BoundFunctionObject {
  def wrapInLets(
    operation: Value,
    realValue: Option[RealValue],
    variables: Seq[Variable],
    location: Option[Location]
  ): Value = {
    variables.foldRight(operation) { case (Variable(name, value), op) =>
      Value.Operation(
        Operation.Let(name, value, op.asBlock, realValue),
        location
      )
    }
  }
}

case class BoundFunctionObject(boundFn: BoundFunction) extends NativeObject(Map(
  "call" -> ScalaVarargMethod({ (c, args, location) =>
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

    val positionalVariables = positionalParams.map { case (name, value) => new Variable(name, value) }
    val namedVariables = namedParams.map { case (name, value) => new Variable(name, value) }

//    val codeWithLets = BoundFunctionObject.wrapInLets(
//      Value.Operation(boundFn.fn.body, location),
//      positionalVariables ++ namedVariables,
//      location
//    )

    Logger("LambdaObject").debug(s"Calling $boundFn with (${args.withoutSelf}) in ${boundFn.scope}")

    val scope = boundFn.scope.newChild(positionalVariables ++ namedVariables)

    val result = c.interpreter.evaluate(
      // TODO: Should this be the location of the function definition instead of the call?
      Value.Operation(boundFn.fn.body, location),
      scope,
//      c.callScope,
//      c.runMode,
//      c.callStack
    )

    result match {
      case result @ Value.Real(RealValue.BoundFn(boundFn), location) =>
        val fn = boundFn.fn
        val wrappedValue = wrapInLets(Operation.Function(fn, None), result, )

      case Value.Real(value, location) =>
      case Value.Operation(operation, location) =>
    }

    result
  }, traits = boundFn.traits, methodId = boundFn.fn.objectId),

  "runTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(FunctionParams.Self)),
    { (_, args, l) =>
      val boundFn = args.getFunction(FunctionParams.Self)
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.CompileTime, FunctionTrait.Partial))

      Value.Real(
        RealValue.BoundFn(
          BoundFunction(boundFn.fn, boundFn.scope, traits)
        ),
        l
      )
    }
  ),

  "compileTimeOnly" -> ScalaMethod(
    MethodOptions(Seq(FunctionParams.Self)),
    { (_, args, l) =>
      val boundFn = args.getFunction(FunctionParams.Self)
      val traits = boundFn.traits.removedAll(Set(FunctionTrait.Runtime, FunctionTrait.Partial))

      Value.Real(
        RealValue.BoundFn(
          BoundFunction(boundFn.fn, boundFn.scope, traits)
        ),
        l
      )
    }
  ),

  "to_bool" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (_, _, l) => Value.Real(RealValue.Boolean(true), l) }
  )
))
