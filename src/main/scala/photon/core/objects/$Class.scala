package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations._
import photon.lib.Lazy

object $Class extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Class.new
    "new" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val className = spec.args.positional.head.evaluate(env)
        val builderFn = spec.args.positional(1).evaluate(env)

        Lazy.selfReferencing[$Object](self => {
          val classBuilder = new ClassBuilder($Lazy(self, location), location)
          val classBuilderObject = $Object(classBuilder, $ClassBuilder, location)

          $Call(
            "call",
            Arguments.positional(builderFn, Seq(classBuilderObject)),
            location
          ).evaluate(env)

          classBuilder.build(env.scope)
        }).resolve
      }
    }
  )
}

case class Class(
  propertyDefs: Seq[ClassDefinition],
  methodDefs: Seq[ClassDefinition],
  // TODO: Can I go without this?
  scope: Scope,
  override val location: Option[Location]
) extends Type {
  val self = this
  val metaType = new Type {
    override def typ(scope: Scope) = $Type
    override val methods = Map(
      // Person.new
      "new" -> new DefaultMethod {
        override val signature = MethodSignature.of(
          propertyDefs.map { property =>
            property.name -> property.value.asType
          },
          self
        )

        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
          val data = propertyDefs
            .map { property => property.name -> spec.require[Value](env, property.name) }
            .toMap

          $Object(data, self, location)
        }
      }
    )
  }

  override def typ(scope: Scope) = metaType
  override val methods = methodsForProperties ++ methodsForFunctions

  private def methodsForProperties =
    propertyDefs.map { property => property.name -> methodForProperty(property) }.toMap

  private def methodsForFunctions =
    methodDefs.map { fn => fn.name -> methodForFunction(fn) }.toMap

  private def methodForProperty(property: ClassDefinition) = new DefaultMethod {
    override val signature = MethodSignature.of(Seq.empty, property.value.asType)
    override def apply(env: Environment, spec: CallSpec, location: Option[Location]) =
      spec.requireSelfObject[Map[String, Value]](env)
        .getOrElse(
          property.name,
          throw EvalError(s"Property ${property.name} not in class definition", location)
        )
        .evaluate(env)
  }

  private def methodForFunction(fnDef: ClassDefinition) = new Method {
    private val callMethod = fnDef.value.typ(scope)
      .method("call")
      .getOrElse { throw EvalError(s"Class method ${fnDef.name} is not callable", location) }

    override val signature: MethodSignature = callMethod.signature

    override def call(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
      val self = spec.requireSelf[$Object](env)
      val argsForFunction = Arguments[Value](
        // Function should be able to get its closure correctly
        self = fnDef.value,
        // First argument should be `self`
        positional = spec.args.positional.prepended(self),
        named = spec.args.named
      )
      val specWithSelfArgument = CallSpec(
        args = argsForFunction,
        bindings = spec.bindings.appended("self" -> self),
        returnType = spec.returnType
      )

      callMethod.call(env, specWithSelfArgument, location)
    }
  }
}

case class ClassDefinition(name: String, value: Value, location: Option[Location])

object $ClassBuilder extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    "define" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(
        Seq("name" -> $String, "definition" -> $AnyStatic),
        $AnyStatic
      )

      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        val self = spec.requireSelfObject[ClassBuilder](env)
        val name = spec.requireObject[String](env, "name")
        val definition = spec.require[Value](env, "definition")

        self.definitions.addOne(ClassDefinition(name, definition, location))

        // TODO: Actual null value
        $Object(null, $Type, location)
      }
    },

    "selfType" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($Type)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = {
        val self = spec.requireSelfObject[ClassBuilder](env)

        self.ref //.evaluate(env)
      }
    }
  )
}

class ClassBuilder(val ref: Value, val location: Option[Location]) {
  var definitions = Seq.newBuilder[ClassDefinition]

  def build(scope: Scope): $Object = {
    val (methodDefs, propertyDefs) = definitions.result
      .partition(_.value.typ(scope).isInstanceOf[$Function])

    val klass = Class(propertyDefs, methodDefs, scope, location)

    $Object(klass, klass.metaType, location)
  }
}
