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
      override val signature = MethodSignature.Any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val (interfaceName, EvalResult(builderFn, _)) = spec.args.positional.head.evaluate(env) match {
          // Using .value should be fine since this is a compile-time only method
          case EvalResult($Object(name: String, _, _), _) => (Some(name), spec.args.positional(1).evaluate(env))
          case builderFn => (None, builderFn)
        }

        var closures: Seq[Closure] = Seq.empty

        val result = Lazy.selfReferencing[Class](self => {
          val classBuilder = new ClassBuilder($Lazy(self, location), location)
          val classBuilderObject = $Object(classBuilder, $ClassBuilder, location)

          // TODO: Is this correct, does this include the closures for the methods of the class/interface?
          val EvalResult(_, innerClosures) = $Call(
            "call",
            Arguments.positional(builderFn, Seq(classBuilderObject)),
            location
          ).evaluate(env)

          closures = innerClosures

          classBuilder.buildClass(env.scope)
        }).resolve

        EvalResult(result, closures)
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
      // TODO: `new` -> `of`
      "new" -> new DefaultMethod {
        override val signature = MethodSignature.of(
          propertyDefs.map { property =>
            property.name -> property.value.asType
          },
          self
        )

        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
          val data = propertyDefs
            // TODO: Require concrete value here, we shouldn't be able to execute this with unknown values
            .map { property => property.name -> spec.require[Value](env, property.name) }
            .toMap

          val closures = data.values.flatMap(_.closures).toSeq
          val values = data.map { case key -> evalResult => key -> evalResult.value }

          val result = $Object(values, self, location)

          EvalResult(result, closures)
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
        .value
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

    private val hasSelfArgument = callMethod.signature.hasSelfArgument

    override val signature: MethodSignature =
      if (hasSelfArgument)
        callMethod.signature.withoutSelfArgument
      else
        callMethod.signature

    override def call(env: Environment, spec: CallSpec, location: Option[Location]) = {
//      val self = spec.requireSelf[$Object](env)
      val self = spec.self
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

      // TODO: Do we want to have this converted to $Call?
      callMethod.call(env, specWithSelfArgument, location)
    }
  }

//  override def evaluate(env: Environment): Value = {
//    val newProps = propertyDefs.map {
//      case ClassDefinition(name, value, location) =>
//        ClassDefinition(name, value.evaluate(env), location)
//    }
//    val newMethods = methodDefs.map {
//      case ClassDefinition(name, value, location) =>
//        ClassDefinition(name, value.evaluate(env), location)
//    }
//
//    Class(newProps, newMethods, scope, location)
//  }
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

      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val self = spec.requireSelfObject[ClassBuilder](env).value
        val name = spec.requireObject[String](env, "name").value
        val definition = spec.require[Value](env, "definition")

        self.definitions.addOne(ClassDefinition(name, definition.value, location))

        // TODO: Actual null value
        val result = $Object(null, $Type, location)

        EvalResult(result, definition.closures)
      }
    },

    "selfType" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.of(Seq.empty, $Type)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val self = spec.requireSelfObject[ClassBuilder](env).value

        EvalResult(self.ref, Seq.empty) //.evaluate(env)
      }
    }
  )
}

class ClassBuilder(val ref: Value, val location: Option[Location]) {
  var definitions = Seq.newBuilder[ClassDefinition]

  def buildClass(scope: Scope) = {
    val (methodDefs, propertyDefs) = definitions.result
      .partition(_.value.typ(scope).isInstanceOf[$Function])

    Class(propertyDefs, methodDefs, scope, location)
  }

  def buildInterface(scope: Scope) = UserDefinedInterface(definitions.result, scope, location)
}
