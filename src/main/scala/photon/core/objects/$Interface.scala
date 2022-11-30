package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations._
import photon.frontend.ASTValue
import photon.lib.Lazy

object $Interface extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Interface.new
    // TODO: Duplication with $Class.new
    "new" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val (interfaceName, EvalResult(builderFn, _)) = spec.args.positional.head.evaluate(env) match {
          // Using .value should be fine since this is a compile-time only method
          case EvalResult($Object(name: String, _, _), _) => (Some(name), spec.args.positional(1).evaluate(env))
          case builderFn => (None, builderFn)
        }

        var closures: Seq[Closure] = Seq.empty

        val result = Lazy.selfReferencing[UserDefinedInterface](self => {
          val classBuilder = new ClassBuilder($Lazy(self, location), location)
          val classBuilderObject = $Object(classBuilder, $ClassBuilder, location)

          // TODO: Is this correct, does this include the closures for the methods of the class/interface?
          val EvalResult(_, innerClosures) = $Call(
            "call",
            Arguments.positional(builderFn, Seq(classBuilderObject)),
            location
          ).evaluate(env)

          closures = innerClosures

          classBuilder.buildInterface(env.scope)
        }).resolve

        EvalResult(result, closures)
      }
    }
  )
}

sealed trait Interface extends Type {
  def canBeAssignedFrom(other: Type): Boolean
}

case class UserDefinedInterface(
  definitions: Seq[ClassDefinition],
  // TODO: Do I need this?
  scope: Scope,
  override val location: Option[Location]
) extends Interface {
  val self = this
  val metaType = new Type {
    override def typ(scope: Scope) = $Type
    override val methods = Map(
      // Named.from
//      "from" -> new DefaultMethod {
//        // TODO: Where should we typecheck?
//        override val signature = MethodSignature.any(self)
//
//        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
//          val value = spec.args.positional.head // spec.require[Value](env, "value")
//
//          $Object(value, self, location)
//        }
//      }
    )
  }

  // TODO: Fix duplication with methodForDefinition
  def canBeAssignedFrom(other: Type): Boolean =
    definitions.forall { case ClassDefinition(methodName, value, location) =>
      value match {
        case returnType: Type =>
          val interfaceSignature = returnType.resolvedType match {
            case fnInterface: FunctionInterface => fnInterface.signature
            case returnType => MethodSignature.of(Seq.empty, returnType)
          }

          val otherSignature = other.method(methodName)
            .getOrElse { return false }
            .signature

          interfaceSignature.canBeAssignedFrom(otherSignature)

        // This is a method defined on the interface, no need to check for that
        case _ => true
      }
    }

  override def typ(scope: Scope): Type = metaType
  override val methods: Map[String, Method] = definitions
    .map { definition => definition.name -> methodForDefinition(definition) }
    .toMap

  private def methodForDefinition(definition: ClassDefinition): Method = {
    definition.value match {
      case returnType: Type =>
        val methodSignature = returnType.resolvedType match {
          case fnInterface: FunctionInterface => fnInterface.signature
          case returnType => MethodSignature.of(Seq.empty, returnType)
        }

        new Method {
          override val signature = methodSignature
          override def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
            val self = spec.requireSelf[InterfaceValue](env)

            $Call(definition.name, spec.args.changeSelf(self.value), location).evaluate(env)
          }
        }

      // TODO: Check if it's callable instead of if it's a function?
      case value if value.typ(scope).resolvedType.isInstanceOf[$Function] => methodForFunction(definition)
    }
  }

  // TODO: This is copy/pasted from $Class.methodForFunction, unify those definitions
  private def methodForFunction(fnDef: ClassDefinition) = new Method {
    private val callMethod = fnDef.value.typ(scope)
      .method("call")
      .getOrElse { throw EvalError(s"Class method ${fnDef.name} is not callable", location) }

    private val hasSelfArgument = callMethod.signature.hasArgumentCalled("self")

    override val signature: MethodSignature =
      if (hasSelfArgument)
        callMethod.signature.withoutFirstArgumentCalled("self")
      else
        callMethod.signature

    override def call(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
      val self = spec.requireSelf[InterfaceValue](env)
      val hasExplicitSelfBinding = spec.bindings.exists { case (name, _) => name == "self" }
      val argsForFunction = Arguments[Value](
        // Function should be able to get its closure correctly
        self = fnDef.value,
        positional = spec.args.positional,
        named =
          if (hasExplicitSelfBinding || !hasSelfArgument) spec.args.named
          else spec.args.named + ("self" -> self.value)
      )
      val specWithSelfArgument = CallSpec(
        args = argsForFunction,
        bindings =
          if (hasExplicitSelfBinding || !hasSelfArgument) spec.bindings
          else spec.bindings.appended("self" -> self.value),
        returnType = spec.returnType
      )

      callMethod.call(env, specWithSelfArgument, location)
    }
  }
}

case class $FunctionInterfaceDef(
  params: Seq[TypeParameter],
  returnType: Value,
  location: Option[Location]
) extends Value {
  override def evalMayHaveSideEffects: Boolean = false
  override def isOperation = true

  override def unboundNames: Set[VarName] =
    params.map(_.typ).flatMap(_.unboundNames).toSet ++ returnType.unboundNames

  // TODO: Cache this
  override def typ(scope: Scope): Type = evaluate(Environment(scope, EvalMode.CompileTimeOnly)).typ(scope)

  override def evaluate(env: Environment): Value = {
    val paramTypes = params.map { param =>
      // This is lazy because we want to be able to self-reference types we're currently defining
      param.name -> $LazyType(Lazy.of(() => {
        param.typ.evaluate(Environment(env.scope, EvalMode.CompileTimeOnly)).asType
      }))
    }

    // TODO: May need this to be $LazyType as well
    val actualReturnType = returnType.evaluate(Environment(env.scope, EvalMode.CompileTimeOnly)).asType

    val signature = MethodSignature.of(
      paramTypes,
      actualReturnType
    )

    // TODO: Should this have unboundNames?
    FunctionInterface(
      signature,
      FunctionRunMode.Default,
      InlinePreference.Default,
      location
    )
  }

  // TODO
  override def toAST(names: Map[VarName, String]): ASTValue = ???
}

case class FunctionInterface(
  signature: MethodSignature,
  runMode: FunctionRunMode,
  inlinePreference: InlinePreference,
  override val location: Option[Location]
) extends Interface {
  val self = this

  override def typ(scope: Scope): Type = $Type
  override val methods = Map("call" -> callMethod)

  override def canBeAssignedFrom(other: Type): Boolean = {
    val otherSignature = other.method("call")
      .getOrElse { return false }
      .signature

    signature.canBeAssignedFrom(otherSignature)
  }

  private def callMethod = new Method {
    override val signature = self.signature
    override def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
      val self = spec.requireSelf[InterfaceValue](env)

      $Call("call", spec.args.changeSelf(self.value), location).evaluate(env)
    }
  }

  // TODO: `toAST`
}

case class InterfaceValue(
  value: Value,
  interface: Interface,
  location: Option[Location]
) extends Value {
  override def isOperation = value.isOperation
  override def evalMayHaveSideEffects = value.evalMayHaveSideEffects
  override def unboundNames = value.unboundNames

  override def typ(scope: Scope) = interface

  // TODO: Will have problems comparing values like this
  override def evaluate(env: Environment) =
    InterfaceValue(
      value.evaluate(env),
      interface,
      location
    )

  override def toAST(names: Map[VarName, String]) = ???
}