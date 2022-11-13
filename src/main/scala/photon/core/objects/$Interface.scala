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
  // TODO: Can I go without this?
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
//      case fn: $Function =>
      case returnType: Type =>
        new Method {
          override val signature: MethodSignature = MethodSignature.of(Seq.empty, returnType)
          override def call(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
            val self = spec.requireSelfObject[Value](env)

//            val method = self.typ(scope)
//              .method(definition.name)
//              .getOrElse { throw EvalError(s"Could not find method ${definition.name} on $self", location) }
//
//            method.call(env, spec, location)

            $Call(definition.name, spec.args.changeSelf(self), location).evaluate(env)
          }
        }
    }
  }
}