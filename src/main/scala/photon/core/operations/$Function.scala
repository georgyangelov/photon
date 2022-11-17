package photon.core.operations

import photon.base._
import photon.core._
import photon.frontend.{ASTParameter, ASTValue, ValuePattern}
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
  pattern: ValuePattern,
  location: Option[Location]
)

case class $FunctionDef(
  params: Seq[Parameter],
  body: Value,
  returnType: Option[Value],
  location: Option[Location]
) extends Value {
  override def evalMayHaveSideEffects = false
  override def isOperation = true
  override def unboundNames: Set[VarName] = {
    val typeNames = ValuePattern.List(params.map(_.pattern)).names

    body.unboundNames -- typeNames.defined -- params.map(_.inName) ++
      typeNames.unbound ++
      returnType.map(_.unboundNames).getOrElse(Set.empty)
  }

  // TODO: Cache this and share with `evaluate`! This gets called multiple times!
  override def typ(scope: Scope) = {
    // TODO: Pattern types
//    val paramTypes = params.map { param =>
//      // This is lazy because we want to be able to self-reference types we're currently defining
//      param.outName -> $LazyType(Lazy.of(() => {
//        param.typ.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).asType
//      }))
//    }

//    val actualReturnType = returnType match {
//      // TODO: May need this to be $LazyType as well
//      case Some(value) => value.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).asType
//      case None =>
//        // This is lazy because methods defined on a class need to be able to infer the return type based
//        // on the class's other methods, which may not be defined yet
//        $LazyType(Lazy.of(() => {
//          val paramTypes = params
//            .map { param => param.inName -> param.typ.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).asType }
//
//          inferReturnType(scope, paramTypes)
//        }))
//    }

//    val signature = MethodSignature.of(
//      args = paramTypes,
//      returnType = actualReturnType
//    )

    val actualReturnType = returnType match {
      // TODO: This evaluation needs to happen after the pattern is matched
      //.evaluate(Environment(scope, EvalMode.CompileTimeOnly)).asType
      case Some(value) => value
      case None =>
        inferReturnTypeIfPossible(scope) match {
          case Some(value) => value
          case None => throw EvalError("Could not infer function return type because it uses type patterns", location)
        }
    }

    val signature = MethodSignature.ofPatterns(
      scope,
      params.map { param => param.outName -> param.pattern },
      actualReturnType
    )

    $Function(signature, FunctionRunMode.Default, InlinePreference.Default)
  }

  private def inferReturnTypeIfPossible(scope: Scope): Option[Type] = {
    val concreteParamValues = params.map {
      case Parameter(_, name, pattern: ValuePattern.Expected, _) => name -> pattern.expectedValue
      case _ => return None
    }

    // This is lazy because methods defined on a class need to be able to infer the return type based
    // on the class's other methods, which may not be defined yet
    Some($LazyType(Lazy.of(() => {
      val env = Environment(scope, EvalMode.CompileTimeOnly)
      val paramTypes = concreteParamValues.map { case name -> value => name -> value.evaluate(env).asType }

      val innerScope = scope.newChild(
        paramTypes.map { case name -> typ => name -> $Object(null, typ, typ.location) }
      )

      body.typ(innerScope)
    })))
  }

//  private def inferReturnType(scope: Scope, paramTypes: Seq[(VarName, Type)]) = {
//    val innerScope = scope.newChild(paramTypes.map { case name -> typ => name -> $Object(null, typ, typ.location) })
//
//    body.typ(innerScope)
//  }

  override def evaluate(env: Environment) =
    $Object(
      Closure(env.scope, this),
      typ(env.scope),
      location
    )

  override def toAST(names: Map[VarName, String]) = {
    val typeParamNames = params
      .map(_.pattern)
      .flatMap(_.names.defined)
      .map { name => name -> findUniqueNameFor(name, names.values.toSet) }
      .toMap

    val paramNames = params
      .map(_.inName)
      .map { name => name -> findUniqueNameFor(name, typeParamNames.values.toSet) }
      .toMap

    ASTValue.Function(
      params.map { param =>
        ASTParameter(
          param.outName,
          paramNames(param.inName),
          Some(param.pattern.toASTWithPreBoundNames(names ++ typeParamNames)),
          param.location
        )
      },

      // TODO: Add `typeParamNames` here if I start support use of type vals in fn body
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

case class Closure(scope: Scope, fnDef: $FunctionDef) {
  // TODO: Add bound values from type patterns
  lazy val names = fnDef.params.map { param => param.inName.originalName -> param.inName }.toMap
}

case class $FunctionMetaType(fnT: $Function) extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "from" -> new DefaultMethod {
      override val signature = MethodSignature.any(fnT)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        // TODO: Replace with pattern MethodSignature when that's available
        val closure = spec.requirePositionalObject[Closure](env, 0)

        $Object(closure, fnT, location)
      }
    }
  )
}

case class $Function(
  signature: MethodSignature,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference
) extends Type {
  val self = this

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
        val closure = spec.requireSelfObject[Closure](env)

        $Object(closure, spec.returnType, location)
      }
    },

    "runTimeOnly" -> new CompileTimeOnlyMethod {
      override lazy val signature = MethodSignature.of(
        args = Seq.empty,
        $Function(self.signature, FunctionRunMode.RunTimeOnly, self.inlinePreference)
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val closure = spec.requireSelfObject[Closure](env)

        $Object(closure, spec.returnType, location)
      }
    },

    "inline" -> new CompileTimeOnlyMethod {
      override lazy val signature = MethodSignature.of(
        args = Seq.empty,
        $Function(self.signature, self.runMode, InlinePreference.ForceInline)
      )
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val closure = spec.requireSelfObject[Closure](env)

        $Object(closure, spec.returnType, location)
      }
    }
  )

  // TODO
  def canBeAssignedFrom(other: Type): Boolean = {
    other match {
      case fnType: $Function => signature.canBeAssignedFrom(fnType.signature)
      case _ => false
    }
  }

  def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
    val evalRequest = this.evalRequest(env.evalMode)

    evalRequest match {
      case FunctionEvalRequest.ForceEval =>
        // We should execute the function fully, nothing left for later
        val selfEnv = Environment(
          env.scope,
          forcedEvalMode(env.evalMode)
        )

        val partialSelf = spec.requireSelf[Value](selfEnv).partialValue(
          selfEnv,
          followReferences = true
        )

        // TODO: Should we check that the lets wrapping this value are fully evaluated as well?
        val closure = partialSelf.value match {
          case $Object(closure: Closure, _, _) => closure
          case value if value.isOperation => throw EvalError(s"Cannot call '$value' compile-time because it's not fully evaluated", location)
          case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
        }

        val execBody = buildBodyForExecution(closure, spec)

        val execEnv = Environment(
          closure.scope,
          forcedEvalMode(env.evalMode)
        )

        execBody.wrapInLets.evaluate(execEnv)

      case FunctionEvalRequest.InlineIfPossible |
           FunctionEvalRequest.InlineIfDefinedInline =>
        val partialSelf = spec.requireSelf[Value](env).partialValue(
          env,
          followReferences = evalRequest == FunctionEvalRequest.InlineIfPossible
        )

        val closure = partialSelf.value match {
          case $Object(closure: Closure, _, _) => closure
          case value if value.isOperation => throw DelayCall
          case _ => throw EvalError("Cannot call 'call' on something that's not a function", location)
        }

        val execEnv = Environment(
          closure.scope,
          env.evalMode
        )
        val execBody = buildBodyForExecution(closure, spec)

        execBody.withOuterVariables(partialSelf.variables).wrapInLets.evaluate(execEnv)
    }
  }

  private def buildBodyForExecution(closure: Closure, spec: CallSpec): PartialValue = {
    val variables = spec.bindings.map { case name -> value => closure.names(name) -> value }

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
          case InlinePreference.Default => FunctionEvalRequest.InlineIfDefinedInline
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
