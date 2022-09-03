package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations._

object $Class extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Class.new
    "new" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val className = spec.args.positional.head.evaluate(env)
        val builderFn = spec.args.positional(1).evaluate(env)

//        Lazy.selfReferencing[Class]
        val classBuilder = new ClassBuilder(location)
        val classBuilderObject = $Object(classBuilder, $ClassBuilder, location)

        $Call(
          "call",
          Arguments.positional(builderFn, Seq(classBuilderObject)),
          location
        ).evaluate(env)

        classBuilder.build(env.scope)
      }
    }
  )
}

case class Class(
  propertyDefs: Seq[ClassDefinition],
  methodDefs: Seq[ClassDefinition],
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
            property.name -> property.value.assertType
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
  override val methods =
    propertyDefs.map { property => property.name -> methodForProperty(property) }.toMap

  private def methodForProperty(property: ClassDefinition) = new DefaultMethod {
    override val signature = MethodSignature.of(Seq.empty, property.value.assertType)
    override def apply(env: Environment, spec: CallSpec, location: Option[Location]) =
      spec.requireSelfObject[Map[String, Value]](env)
        .getOrElse(
          property.name,
          throw EvalError(s"Property ${property.name} not in class definition", location)
        )
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
    }
  )
}

class ClassBuilder(val location: Option[Location]) {
  var definitions = Seq.newBuilder[ClassDefinition]

  def build(scope: Scope): $Object = {
    val (methodDefs, propertyDefs) = definitions.result
      .partition(_.value.typ(scope).isInstanceOf[$Function])

    val klass = Class(propertyDefs, methodDefs, location)

    $Object(klass, klass.metaType, location)
  }
}

//object $Class extends Type {
//  override def typ(scope: Scope) = $Type
//  override val methods = Map(
//    // Class.new
//    "new" -> new CompileTimeOnlyMethod {
//      override val signature = MethodSignature.any($AnyStatic)
//      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
//        val classDef = ClassDefinition(spec.args.named)
//
//        $Object(classDef, classDef.typ, location)
//      }
//    }
//  )
//}

//case class ClassDefinition(members: Map[String, Value]) {
//  val typ = new Type {
//    override def typ(scope: Scope) = $Type
//    override val methods = Map(
//      // User.new
//      "new" -> new DefaultMethod {
//        override val signature = MethodSignature.of(
//
//        )
//        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]): Value = ???
//      }
//    )
//  }
//
//  private def properties(scope: Scope): Map[String, Value] = {
//    members.filter(_._2.typ())
//  }
//}