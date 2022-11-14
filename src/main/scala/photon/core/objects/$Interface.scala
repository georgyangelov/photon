package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations.{$Call, $Function}
import photon.lib.Lazy

object $Interface extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Interface.new
    "new" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val interfaceName = spec.args.positional.head.evaluate(env)
        val builderFn = spec.args.positional(1).evaluate(env)

        Lazy.selfReferencing[Interface](self => {
          val classBuilder = new ClassBuilder($Lazy(self, location), location)
          val classBuilderObject = $Object(classBuilder, $ClassBuilder, location)

          $Call(
            "call",
            Arguments.positional(builderFn, Seq(classBuilderObject)),
            location
          ).evaluate(env)

          classBuilder.buildInterface(env.scope)
        }).resolve
      }
    }
  )
}

case class Interface(
  definitions: Seq[ClassDefinition],
  // TODO: Do I need this?
  scope: Scope,
  override val location: Option[Location]
) extends Type {
  val self = this
  val metaType = new Type {
    override def typ(scope: Scope) = $Type
    override val methods = Map(
      // Named.from
      "from" -> new DefaultMethod {
        // TODO: Where should we typecheck?
        override val signature = MethodSignature.of(
          Seq("value" -> $AnyStatic),
          self
        )

        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
          val value = spec.require[Value](env, "value")

          $Object(value, self, location)
        }
      }
    )
  }

  override def typ(scope: Scope): Type = metaType
  override val methods: Map[String, Method] = definitions
    .map { definition => definition.name -> methodForDefinition(definition) }
    .toMap

  private def methodForDefinition(definition: ClassDefinition): Method = {
    definition.value match {
      case returnType: Type =>
        new Method {
          override val signature: MethodSignature = MethodSignature.of(Seq.empty, returnType)
          override def call(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
            val self = spec.requireSelfObject[Value](env)

            $Call(definition.name, spec.args.changeSelf(self), location).evaluate(env)
          }
        }

      // TODO: Check if it's callable instead of if it's a function?
      case value if value.typ(scope).isInstanceOf[$Function] => methodForFunction(definition)
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
      val self = spec.requireSelf[$Object](env)
      val hasExplicitSelfBinding = spec.bindings.exists { case (name, _) => name == "self" }
      val argsForFunction = Arguments[Value](
        // Function should be able to get its closure correctly
        self = fnDef.value,
        positional = spec.args.positional,
        named =
          if (hasExplicitSelfBinding || !hasSelfArgument) spec.args.named
          else spec.args.named + ("self" -> self)
      )
      val specWithSelfArgument = CallSpec(
        args = argsForFunction,
        bindings =
          if (hasExplicitSelfBinding || !hasSelfArgument) spec.bindings
          else spec.bindings.appended("self" -> self),
        returnType = spec.returnType
      )

      callMethod.call(env, specWithSelfArgument, location)
    }
  }
}