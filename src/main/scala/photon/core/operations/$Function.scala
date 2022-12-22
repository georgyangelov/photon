package photon.core.operations

import photon.base._
import photon.core._
import photon.frontend._
import photon.lib.Lazy
import photon.lib.ScalaExtensions._

case class TypeParameter(
  name: String,
  typ: Value,
  location: Option[Location]
)

case class Parameter(
  outName: String,
  inName: VarName,
  typ: Value,
  location: Option[Location]
)

case class $FunctionDef(
  params: Seq[Parameter],
  var body: Value,
  returnType: Option[Value],
  location: Option[Location]
) extends Value {
  override def evalMayHaveSideEffects = false
  override def isOperation = true

  override def unboundNames: Set[VarName] =
    body.unboundNames -- params.map(_.inName) ++
      params.map(_.typ).flatMap(_.unboundNames) ++
      returnType.map(_.unboundNames).getOrElse(Set.empty)

  // TODO: Cache this and share with `evaluate`! This gets called multiple times!
  override def typ(scope: Scope) = {
    val actualParamTypes = params.map { param =>
      val realType = $LazyType(Lazy.of(() =>
        param.typ.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).value.asType
      ))

      param -> realType
    }

    // TODO: The return type can be inferred for generic functions when they're used
    val actualReturnType = returnType match {
      case Some(value) =>
        $LazyType(Lazy.of(() =>
          value.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).value.asType
        ))

      case None =>
        val inParamTypes = actualParamTypes.map { case param -> typ => param.inName -> typ }

        inferReturnTypeIfPossible(scope, inParamTypes) match {
          case Some(value) => value
          case None => throw EvalError("Could not infer function return type because it uses type patterns", location)
        }
    }

    val signature = MethodSignature.of(
      actualParamTypes.map { case param -> typ => param.outName -> typ },
      actualReturnType
    )

    $Function(signature, FunctionRunMode.Default, InlinePreference.Default)
  }

  private def inferReturnTypeIfPossible(scope: Scope, paramTypes: Seq[(VarName, Type)]): Option[Type] = {
    // This is lazy because methods defined on a class need to be able to infer the return type based
    // on the class's other methods, which may not be defined yet
    Some($LazyType(Lazy.of(() => {
      val innerScope = scope.newChild(
        paramTypes.map { case name -> typ => name -> $Object(null, typ, typ.location) }
      )

      body.typ(innerScope)
    })))
  }

  override def evaluate(env: Environment) = {
    val closure = Closure(env.scope, this, typ(env.scope))

    EvalResult(closure, Seq(closure))
  }

  // This should only be called from Closure as only it knows what the expected evalMode of the function is
  def evaluatePartially(env: Environment): Unit = {
    val unknownValuesForParams = params
      // TODO: This evaluate here is duplicated with the .typ function above
      .map { param =>
        param.inName -> $Unknown(
          param.typ
            .evaluate(Environment(env.scope, EvalMode.CompileTimeOnly))
            .value
            .asType,
          param.location
        )
      }

    val innerScope = env.scope.newChild(unknownValuesForParams)
    val innerEnv = Environment(innerScope, env.evalMode)

    val newBody = body.evaluate(innerEnv)

    this.body = newBody.value

    newBody.partiallyEvaluateInnerClosures(innerEnv)
  }

  override def toAST(names: Map[VarName, String]) = {
    val paramNames = params
      .map(_.inName)
      .map { name => name -> findUniqueNameFor(name, names.values.toSet) }
      .toMap

    ASTValue.Function(
      params.map { param =>
        ASTParameter(
          param.outName,
          paramNames(param.inName),
          Some(Pattern.SpecificValue(param.typ.toAST(names ++ paramNames))),
          param.location
        )
      },
      body = body.toAST(names ++ paramNames),
      returnType = returnType.map(_.toAST(names)),
      location
    )
  }

  // TODO: Deduplicate this with $Let#findUniqueName
  private def findUniqueNameFor(name: VarName, usedNames: Set[String]): String = {
    // TODO: Define `Value#unboundNames` and check if the duplicate name is actually used before renaming
    if (!usedNames.contains(name.originalName)) {
      return name.originalName
    }

    var i = 1
    while (usedNames.contains(s"${name.originalName}__$i")) {
      i += 1
    }

    s"${name.originalName}__$i"
  }
}

case class Closure(scope: Scope, fnDef: $FunctionDef, typ: $Function) extends Value {
  // TODO: Add bound values from type patterns
  lazy val names = fnDef.params.map { param => param.inName.originalName -> param.inName }.toMap

  override def evalMayHaveSideEffects = false
  override def location = fnDef.location
  override def unboundNames = fnDef.unboundNames

  override def typ(scope: Scope) = typ

  override def toAST(names: Map[VarName, String]) = fnDef.toAST(names)

  def evaluatePartially(env: Environment): Unit = {
    // TODO: This should also depend on the parent environment, probably
    val partialEvalMode = typ.runMode match {
      // TODO: This should result in an error - the function was not evaluated compile-time.
      //       Or maybe it was but wasn't removed
      case FunctionRunMode.CompileTimeOnly => return
      case FunctionRunMode.Default => EvalMode.Partial
      case FunctionRunMode.PreferRunTime => EvalMode.PartialPreferRunTime
      case FunctionRunMode.RunTimeOnly => EvalMode.PartialRunTimeOnly
    }

    val innerEnv = Environment(scope, partialEvalMode)
    fnDef.evaluatePartially(innerEnv)
  }

  override def evaluate(env: Environment): EvalResult[Value] =
    // TODO: Is this correct? We should have returned the closure when it was defined, but still it's weird.
    //       Is this even being called at all?
    EvalResult(this, Seq.empty)
}

case class $FunctionMetaType(fnT: $Function) extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map.empty
}

case class $Function(
  signature: MethodSignature,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference
) extends Type {
  val self = this

  // TODO: Add `unboundNames` here because of the signature
  override def unboundNames: Set[VarName] = ???

  override def typ(scope: Scope) = $FunctionMetaType(this)
  override val methods = Map(
    "call" -> new Method {
      override val signature = self.signature

      override def call(env: Environment, spec: CallSpec, location: Option[Location]) =
        self.call(env, spec, location)
    },

    "compileTimeOnly" -> new CompileTimeOnlyMethod {
      override lazy val signature = MethodSignature.of(
        args = Seq.empty,
        $Function(self.signature, FunctionRunMode.CompileTimeOnly, self.inlinePreference)
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(oldClosure, closures) = spec.requireSelf[Closure](env)
        val newClosure = Closure(oldClosure.scope, oldClosure.fnDef, spec.returnType.asInstanceOf[$Function])

        EvalResult(newClosure, closures.except(oldClosure) :+ newClosure)
      }
    },

    "runTimeOnly" -> new CompileTimeOnlyMethod {
      override lazy val signature = MethodSignature.of(
        args = Seq.empty,
        $Function(self.signature, FunctionRunMode.RunTimeOnly, self.inlinePreference)
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(oldClosure, closures) = spec.requireSelf[Closure](env)
        val newClosure = Closure(oldClosure.scope, oldClosure.fnDef, spec.returnType.asInstanceOf[$Function])

        EvalResult(newClosure, closures.except(oldClosure) :+ newClosure)
      }
    },

    "inline" -> new CompileTimeOnlyMethod {
      override lazy val signature = MethodSignature.of(
        args = Seq.empty,
        $Function(self.signature, self.runMode, InlinePreference.ForceInline)
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val EvalResult(oldClosure, closures) = spec.requireSelf[Closure](env)
        val newClosure = Closure(oldClosure.scope, oldClosure.fnDef, spec.returnType.asInstanceOf[$Function])

        EvalResult(newClosure, closures.except(oldClosure) :+ newClosure)
      }
    }
  )

  def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
    val evalRequest = this.evalRequest(env.evalMode)

    evalRequest match {
      case FunctionEvalRequest.ForceEval =>
        // We should execute the function fully, nothing left for later
        val selfEnv = Environment(
          env.scope,
          forcedEvalMode(env.evalMode)
        )

        val EvalResult(partialSelf, partialSelfClosures) = spec.requireSelf[Value](selfEnv)
          .mapValue(_.partialValue(selfEnv, followReferences = true))

        // TODO: Should we check that the lets wrapping this value are fully evaluated as well?
        val closure = partialSelf.value match {
          case closure: Closure => closure
          case value if value.isOperation => throw EvalError(s"Cannot call '$value' compile-time because it's not fully evaluated", location)
          case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
        }

        val execBody = buildBodyForExecution(closure, spec, env.scope)

        val execEnv = Environment(
          closure.scope,
          forcedEvalMode(env.evalMode)
        )

        val result = execBody.wrapInLets.evaluate(execEnv)

        EvalResult(result.value, partialSelfClosures ++ result.closures)

      case FunctionEvalRequest.InlineIfPossible |
           FunctionEvalRequest.InlineIfDefinedInline =>
        val EvalResult(partialSelf, partialSelfClosures) = spec.requireSelf[Value](env)
          .mapValue(_.partialValue(env, followReferences = evalRequest == FunctionEvalRequest.InlineIfPossible))

        // TODO: This is not correct - calls not only inlined functions but also functions on classes
        val closure = partialSelf.value match {
          case closure: Closure => closure
          case value if value.isOperation => throw DelayCall
          case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
        }

        val execEnv = Environment(
          closure.scope,
          env.evalMode
        )
        val execBody = buildBodyForExecution(closure, spec, env.scope)

        val result = execBody.withOuterVariables(partialSelf.variables).wrapInLets.evaluate(execEnv)

        EvalResult(result.value, partialSelfClosures ++ result.closures)
    }
  }

  private def buildBodyForExecution(closure: Closure, spec: CallSpec, argsScope: Scope): PartialValue = {
    val variables = spec.bindings.map {
      case name -> value =>
        closure.names(name) -> $ScopeBound(value, argsScope)
    }

    PartialValue(closure.fnDef.body, variables)
  }

  private def evalRequest(evalMode: EvalMode) = evalMode match {
    case EvalMode.RunTime => runMode match {
      case FunctionRunMode.CompileTimeOnly => throw CannotCallCompileTimeMethodInRunTimeMethod
      case FunctionRunMode.Default => FunctionEvalRequest.ForceEval
      case FunctionRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
      case FunctionRunMode.RunTimeOnly => FunctionEvalRequest.ForceEval
    }

    case EvalMode.CompileTimeOnly => runMode match {
      case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
      case FunctionRunMode.Default => FunctionEvalRequest.ForceEval
      case FunctionRunMode.PreferRunTime => FunctionEvalRequest.ForceEval
      case FunctionRunMode.RunTimeOnly => throw CannotCallRunTimeMethodInCompileTimeMethod
    }

    case EvalMode.Partial => runMode match {
      case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
      case FunctionRunMode.Default =>
        inlinePreference match {
          case InlinePreference.ForceInline => FunctionEvalRequest.InlineIfPossible
//          TODO: Implement this another way when this does not always try to inline class methods
//          case InlinePreference.Default => FunctionEvalRequest.InlineIfDefinedInline
          case InlinePreference.Default => throw DelayCall
          case InlinePreference.NoInline => throw DelayCall
        }

      case FunctionRunMode.PreferRunTime => throw DelayCall
      case FunctionRunMode.RunTimeOnly => throw DelayCall
    }

    case EvalMode.PartialRunTimeOnly |
         EvalMode.PartialPreferRunTime => runMode match {
      case FunctionRunMode.CompileTimeOnly => FunctionEvalRequest.ForceEval
      case FunctionRunMode.Default => throw DelayCall
      case FunctionRunMode.PreferRunTime => throw DelayCall
      case FunctionRunMode.RunTimeOnly => throw DelayCall
    }
  }

  private def forcedEvalMode(evalMode: EvalMode) = evalMode match {
    case EvalMode.RunTime => EvalMode.RunTime
    case EvalMode.CompileTimeOnly |
         EvalMode.Partial |
         EvalMode.PartialRunTimeOnly |
         EvalMode.PartialPreferRunTime => EvalMode.CompileTimeOnly
  }
}

sealed trait FunctionRunMode
object FunctionRunMode {
  case object CompileTimeOnly extends FunctionRunMode
  case object Default         extends FunctionRunMode
  case object PreferRunTime   extends FunctionRunMode
  case object RunTimeOnly     extends FunctionRunMode
}

sealed trait InlinePreference
object InlinePreference {
  case object ForceInline extends InlinePreference
  case object Default     extends InlinePreference
  case object NoInline    extends InlinePreference
}

sealed trait FunctionEvalRequest
object FunctionEvalRequest {
  case object ForceEval extends FunctionEvalRequest
  case object InlineIfPossible extends FunctionEvalRequest
  case object InlineIfDefinedInline extends FunctionEvalRequest
}
