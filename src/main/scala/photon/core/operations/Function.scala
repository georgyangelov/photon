package photon.core.operations

import photon.core.{Core, InlinePreference, Method, MethodRunMode, StandardType, Type, TypeRoot, UnknownValue}
import photon.interpreter.{EvalError, URename}
import photon.{Arguments, EValue, EValueContext, EvalMode, Location, Scope, UFunction, UOperation, UParameter, UValue, Variable, VariableName}

object FunctionDef extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}

case class FunctionDefValue(fn: photon.UFunction, scope: Scope, location: Option[Location]) extends EValue {
  override def isOperation = true
  override val typ = FunctionDef
  override def unboundNames = fn.unboundNames

  override def evalMayHaveSideEffects = false
  override def evalType = Some(evaluated.typ)

  // TODO: Should this indirection be here at all?
  //       Maybe when type inference for parameters is implemented?
  override protected def evaluate: EValue = {
    val context = EValue.context
    val eParams = fn.params.map { param =>
      val argType = context.toEValue(param.typ, scope)

      EParameter(param.name, argType, location)
    }

    val eReturnType = fn.returnType
      .map(context.toEValue(_, scope))
      .getOrElse { inferReturnType(context, eParams) }

    val functionType = FunctionT(eParams, eReturnType, MethodRunMode.Default, InlinePreference.Default)

    FunctionValue(functionType, fn.nameMap, fn.body, scope, location)
  }

  override def finalEval = evaluated.finalEval

  override def toUValue(core: Core) = UOperation.Function(fn, location)

  private def inferReturnType(context: EValueContext, eparams: Seq[EParameter]) = {
    val partialScope = scope.newChild(eparams.map { param =>
      Variable(fn.nameMap(param.name), UnknownValue(param.typ.evalAssert[Type], param.location))
    })
    val ebody = context.toEValue(fn.body, partialScope)

    ebody.evalType.getOrElse(ebody.typ)
  }
}

object FunctionRootType extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "call" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) = FunctionRoot
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

        // TODO: This should actually return an interface and should only have a runMode if it has a known object inside
        FunctionT(params, returnType, MethodRunMode.Default, InlinePreference.Default)
      }
    }
  )
}

object FunctionRoot extends StandardType {
  override def typ = FunctionRootType

  // TODO: These should probably reference the core.referenceTo(this, ...)
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class FunctionT(
  params: Seq[EParameter],
  returnType: EValue,
  runMode: MethodRunMode,
  inlinePreference: InlinePreference
) extends StandardType {
  override def typ = FunctionRoot
  override val location = None

  private[this] val self = this

  override def finalEval = FunctionT(
    params.map(p => EParameter(p.name, p.typ.finalEval, p.location)),
    returnType.finalEval,
    runMode,
    inlinePreference
  )

  override val methods = Map(
    "returnType" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) = TypeRoot
      override def call(args: Arguments[EValue], location: Option[Location]) = returnType
    },

    "runTimeOnly" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) =
        FunctionT(params, returnType, MethodRunMode.RunTimeOnly, inlinePreference)

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, MethodRunMode.RunTimeOnly, inlinePreference)
        val fn = args.self.evalAssert[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "compileTimeOnly" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) =
        FunctionT(params, returnType, MethodRunMode.CompileTimeOnly, inlinePreference)

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, MethodRunMode.CompileTimeOnly, inlinePreference)
        val fn = args.self.evalAssert[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "inline" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) =
        FunctionT(params, returnType, runMode, InlinePreference.ForceInline)

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, runMode, InlinePreference.ForceInline)
        val fn = args.self.evalAssert[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "noInline" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) =
        FunctionT(params, returnType, runMode, InlinePreference.NoInline)

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, runMode, InlinePreference.NoInline)
        val fn = args.self.evalAssert[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }
    },

    "call" -> new Method {
      // TODO: Add traits to the type itself
      override val runMode = self.runMode
      override def typeCheck(args: Arguments[EValue]) = returnType.evalAssert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]): EValue = {
        val partialSelf = args.self.evaluated match {
          case letValue: LetValue => letValue.partialValue
          case innerValue => PartialValue(innerValue, Seq.empty)
        }

        // TODO: This is incorrect, it will not always have the function value known
        val fn = partialSelf.value.evalAssert[FunctionValue]

        // TODO: Also check if it CAN be evaluated (i.e. if the values are "real")?
        // TODO: This check should be done in CallValue#evaluate
//        if (fn.typ.runMode)
//        if (!fn.typ.traits.contains(MethodTrait.CompileTime)) {
//          return CallValue("call", args, location)
//        }

        // TODO: Better arg check + do it in the typeCheck method
        if (args.positional.size + args.named.size != params.size) {
          throw EvalError("Wrong number of arguments for this function", location)
        }

        val paramNames = params.map(_.name)

        val matchedArguments = matchArguments(paramNames, args)

        val localVariables = matchedArguments.map { case name -> value =>
          value match {
            case reference: ReferenceValue => (name, reference.variable, true)
            case _ => (name, Variable(new VariableName(name), value), false)
          }
        }

        val renames = localVariables
          .map { case (name, variable, _) => fn.nameMap(name) -> variable.name }
          .toMap

        val scope = fn.scope.newChild(localVariables.map(_._2))

        val renamedUBody = URename.rename(fn.body, renames)
        val ebody = EValue.context.toEValue(renamedUBody, scope)

        val bodyWrappedInLets = partialSelf
          .addInnerVariables(
            // TODO: Preserve order of definition so that latter variables can use prior ones
            localVariables
              .filter { case (_, _, isFromParentScope) => !isFromParentScope }
              .map(_._2)
          )
          .replaceWith(ebody)
          .wrapBack

        bodyWrappedInLets.evaluated
      }
    }
  )

  private def matchArguments(paramNames: Seq[String], args: Arguments[EValue]): Seq[(String, EValue)] = {
    val positionalNames = paramNames.filterNot(args.named.contains)

    val positionalParams = positionalNames.zip(args.positional)
    val namedParams = args.named.toSeq

    positionalParams ++ namedParams

//    val namedParams = namesOfnamedParams.map { name =>
//      // TODO: This should use the name of the actual parameter always, it should not try to rename it.
//      //       This means parameters may have an "external" name and an "internal" name, probably something
//      //       like `sendEmail = (to as address, subject) { # This uses address and not to }; sendEmail(to = 'email@example.com')`
//      args.named.get(name) match {
//        case Some(value) => (name, value)
//        case None => throw EvalError(s"Argument $name not specified in method call", location)
//      }
//    }


  }

  // TODO: Add reference to FunctionRoot?
  override def unboundNames = params.flatMap(_.typ.unboundNames).toSet

  // TODO: Preserve order of definition so that latter variables can use prior ones
  override def toUValue(core: Core) =
    UOperation.Call(
      "call",
      Arguments.named(
        core.referenceTo(FunctionRoot, location),
        params.map { param => param.name -> param.typ.toUValue(core) }.toMap +
          ("returns" -> returnType.toUValue(core))
      ),
      location
    )
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
  override def unboundNames =
    typ.params.flatMap(_.typ.unboundNames).toSet ++
      typ.returnType.unboundNames ++
      body.unboundNames -- nameMap.values

  override def evalType = None
  override def evalMayHaveSideEffects = false
  override protected def evaluate: EValue = this

  override def finalEval = {
    val partialScope = scope.newChild(typ.params.map { param =>
      Variable(nameMap(param.name), UnknownValue(param.typ.evalAssert[Type], param.location))
    })

    val partialContext = EValue.context.copy(evalMode = EvalMode.Partial(typ.runMode))
    val ebody = EValue.withContext(partialContext) {
      partialContext.toEValue(body, partialScope).evaluated.finalEval
    }
    val finalBody = ebody.toUValue(EValue.context.core)

    FunctionValue(typ, nameMap, finalBody, scope, location)
  }

  // TODO: Encode method traits with `.compileTimeOnly` / `.runTimeOnly`
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
