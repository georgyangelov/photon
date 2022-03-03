package photon.core.operations

import photon.compiler.{Compiler, CCode, CompileContext}
import photon.core.{Core, Method, MethodTrait, StandardType, Type, TypeRoot, UnknownValue}
import photon.interpreter.{EvalError, Interpreter, URename}
import photon.{Arguments, EValue, Location, Scope, UFunction, UOperation, UParameter, UValue, Variable, VariableName}

object FunctionDef extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
  override def compile(context: CompileContext) = uncompilable
}

case class FunctionDefValue(fn: photon.UFunction, scope: Scope, location: Option[Location]) extends EValue {
  override val typ = FunctionDef
  override def unboundNames = fn.unboundNames

  override def evalMayHaveSideEffects = false
  override def evalType = Some(evaluate.typ)

  override def compile(context: CompileContext) = evaluate.compile(context)

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

    val functionType = FunctionT(eParams, eReturnType, Set(MethodTrait.CompileTime, MethodTrait.RunTime))

    // TODO: This should include side-effects?
    FunctionValue(functionType, fn.nameMap, fn.body, scope, location)
  }

  override def toUValue(core: Core) = UOperation.Function(fn, location)

  private def inferReturnType(eparams: Seq[EParameter]) = {
    val partialScope = scope.newChild(eparams.map { param =>
      Variable(fn.nameMap(param.name), UnknownValue(param.typ.evalAssert[Type], param.location))
    })
    val ebody = Interpreter.current.toEValue(fn.body, partialScope)

    ebody.evalType.getOrElse(ebody.typ)
  }
}

object FunctionRootType extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override def compile(context: CompileContext) = uncompilable
  override val methods = Map(
    "call" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
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

        // TODO: This should include side-effects?
        FunctionT(params, returnType, Set(MethodTrait.CompileTime, MethodTrait.RunTime))
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
  override def compile(context: CompileContext) = uncompilable
}

case class FunctionT(params: Seq[EParameter], returnType: EValue, traits: Set[MethodTrait]) extends StandardType {
  override def typ = FunctionRoot
  override val location = None
  override def compile(context: CompileContext) = {
    context.compiler.defineType(this, "TODOFunctionType", "// Lambda type definition\n")

    CCode.EmptyBlock
  }

  private[this] val self = this

  override val methods = Map(
    "returnType" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(args: Arguments[EValue]) = TypeRoot
      override def call(args: Arguments[EValue], location: Option[Location]) = returnType
    },

    "runTimeOnly" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(args: Arguments[EValue]) = FunctionT(params, returnType, Set(MethodTrait.RunTime))
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val newType = FunctionT(params, returnType, Set(MethodTrait.RunTime))
        val fn = args.self.evalAssert[FunctionValue]

        FunctionValue(newType, fn.nameMap, fn.body, fn.scope, location)
      }

      override def compile(compiler: Compiler) = {
        compiler.defineFunction(this, "runTimeOnly", "// RuntimeOnly type definition\n")
      }
    },

    "call" -> new Method {
      // TODO: Add traits to the type itself
      override val traits = self.traits
      override def typeCheck(args: Arguments[EValue]) = returnType.evalAssert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]): EValue = {
        // TODO: Add self argument
        // TODO: Handle unevaluated arguments?

        val partialSelf = args.self.evaluated match {
          case letValue: LetValue => letValue.partialValue
          case innerValue => PartialValue(innerValue, Seq.empty)
        }

        // TODO: This is incorrect, it will not always have the function value known
        val fn = partialSelf.value.evalAssert[FunctionValue]

        // TODO: Also check if it CAN be evaluated (i.e. if the values are "real")?
        if (!fn.typ.traits.contains(MethodTrait.CompileTime)) {
          return CallValue("call", args, location)
        }

        // TODO: Better arg check + do it in the typeCheck method
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

        val localVariables = (positionalParams ++ namedParams).map { case name -> value =>
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
        val ebody = Interpreter.current.toEValue(renamedUBody, scope)

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

      override def compile(compiler: Compiler) = {
        val fnName = s"CALL_ANON_${objectId.id}"

        compiler.defineFunction(this, fnName, s"""
          #define $fnName(__lambdaRef__) __lambdaRef__.fn()
        """)

//        val fnName = s"anon$$${objectId.id}$$call"
//        val fnContext = context.newFunction("result")
//
//        val cReturnType = fnContext.requireType(returnType)
//
//        fnContext.code.append(s"$cReturnType $fnName() {\n")
//
//
//
//        fnContext.code.append("return result;\n")
//        fnContext.code.append("}\n")
//        fnContext.endFunction()
//
//        context.compiler.functions.addOne(this -> fnName)
      }
    }
  )

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

  override def compile(context: CompileContext) = {
    context.compiler.defineType(this, "TheFunctionStruct", "// TheFunctionStruct definition")

    // TODO: This should create an instance of the structure,
    //       referencing variables from the scope (unboundNames)
    val block = context.newBlock

    block.addStatement("TheFunctionStruct fn = { .scope = varFromScope }")
    block.addReturn("fn")

    block.build
  }
}
