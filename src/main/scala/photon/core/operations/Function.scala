package photon.core.operations

import photon.core.{Core, Method, MethodTrait, StandardType, Type, TypeRoot, UnknownValue}
import photon.interpreter.{EvalError, Interpreter}
import photon.{Arguments, EValue, Location, Scope, UFunction, UOperation, UParameter, UValue, Variable, VariableName}

object FunctionDef extends StandardType {
  override val typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class FunctionDefValue(fn: photon.UFunction, scope: Scope, location: Option[Location]) extends EValue {
  override val typ = FunctionDef

  override def evalMayHaveSideEffects = false
  override def evalType = Some(evaluate.typ)

  // TODO: Should this indirection be here at all?
  //       Maybe when type inference for parameters is implemented?
  override protected def evaluate: EValue = {
    val interpreter = Interpreter.current
    val eParams = fn.params.map { param =>
      val argType = interpreter.toEValue(param.typ, scope)

      EParameter(param.name, argType, location)
    }

    val eReturnType = fn.returnType
      .map(interpreter.toEValue(_, scope))
      .getOrElse { inferReturnType(eParams) }

    val functionType = FunctionT(eParams, eReturnType)

    FunctionValue(functionType, fn.nameMap, fn.body, scope, location)
  }

  override def toUValue(core: Core) = UOperation.Function(fn, location)

  private def inferReturnType(eparams: Seq[EParameter]) = {
    val partialScope = scope.newChild(eparams.map { param =>
      Variable(fn.nameMap(param.name), UnknownValue(param.typ.evaluated.assert[Type], param.location))
    })
    val ebody = Interpreter.current.toEValue(fn.body, partialScope)

    ebody.evalType.getOrElse(ebody.typ)
  }
}

object FunctionRootType extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "call" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(argumentTypes: Arguments[Type]) = FunctionRoot
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        if (args.positional.nonEmpty) {
          throw EvalError("Function type parameters must be named", location)
        }

        val returnType = args.named.getOrElse(
          "returns",
          throw EvalError("Function type must have a `returns` argument", location)
        )
        val params = (args.named - "returns")
          .map { case name -> etype => EParameter(name, etype, etype.location) }
          .toSeq

        FunctionT(params, returnType)
      }
    }
  )
}

object FunctionRoot extends StandardType {
  override def typ = FunctionRootType
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class FunctionT(params: Seq[EParameter], returnType: EValue) extends StandardType {
  override def typ = FunctionRoot
  override val location = None

  override val methods = Map(
    "returnType" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(argumentTypes: Arguments[Type]) = TypeRoot
      override def call(args: Arguments[EValue], location: Option[Location]) = returnType
    },

    "call" -> new Method {
      // TODO: Add traits to the type itself
      override val traits = Set(MethodTrait.RunTime, MethodTrait.CompileTime)
      override def typeCheck(argumentTypes: Arguments[Type]) = returnType.evaluated.assert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        // TODO: Add self argument
        // TODO: Handle unevaluated arguments?

        val fn = args.self.assert[FunctionValue]

        // TODO: Better arg check
        if (args.positional.size + args.named.size != params.size) {
          throw EvalError("Wrong number of arguments for this function", location)
        }

        val paramNames = params.map(_.name)

        val positionalParams = paramNames.zip(args.positional)
        val namesOfnamedParams = paramNames.drop(args.positional.size).toSet
        val namedParams = namesOfnamedParams.map { name =>
          // TODO: This should use the name of the actual parameter always, it should not try to rename it.
          //       This means parameters may have an "external" name and an "internal" name, probably something
          //       like `sendEmail = (to as address, subject) { # This uses address and not to }; sendEmail(to = 'email@example.com')`
          args.named.get(name) match {
            case Some(value) => (name, value)
            case None => throw EvalError(s"Argument $name not specified in method call", location)
          }
        }

        val positionalVariables = positionalParams
          .map { case (name, value) => Variable(fn.nameMap(name), value) }

        val namedVariables = namedParams
          .map { case (name, value) => Variable(fn.nameMap(name), value) }

        val scope = fn.scope.newChild(positionalVariables ++ namedVariables)

        val result = Interpreter.current.evaluateInScope(fn.body, scope)

        result
      }
    }
  )

  // TODO: This needs to become convertible to a function call building the type
  override def toUValue(core: Core) = inconvertible
}

case class EParameter(name: String, typ: EValue, location: Option[Location]) {
  def toUParameter(core: Core) = UParameter(name, typ.toUValue(core), location)
}

case class FunctionValue(
  typ: FunctionT,
  nameMap: Map[String, VariableName],
  body: UValue,
  scope: Scope,
  location: Option[Location]
) extends EValue {
  override def evalType = None
  override def evalMayHaveSideEffects = false
  override protected def evaluate: EValue = this

  override def toUValue(core: Core) = UOperation.Function(
    new UFunction(
      typ.params.map(_.toUParameter(core)),
      nameMap,
      body,
      Some(typ.returnType.toUValue(core))
    ),
    location
  )
}
