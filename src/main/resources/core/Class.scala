package photon.core
import photon.ArgumentExtensions._
import photon.core.operations.FunctionValue
import photon.interpreter.EvalError
import photon.lib.Lazy
import photon.{Arguments, CompileTimeOnlyMethod, DefaultMethod, EValue, EvalMode, Location, Method, MethodType}

object ClassBuilderRoot extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "define" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          Seq(
            "name" -> String,
            "def" -> StaticType
          ),
          String
        )

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[ClassBuilder]
        val name = args.getEval[StringValue](1, "name")
        val definition = args.getEval[EValue](2, "def")

        self.definitions.addOne(ClassDefinition(name.value, definition, location))

        name
      }
    },

    "selfType" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(Seq.empty, TypeRoot)

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[ClassBuilder]

        self.ref.evaluated
      }
    }
  )
}

class ClassBuilder(
  val name: Option[String],
  val ref: EValue,
  val location: Option[Location]
) extends EValue {
  val definitions = Seq.newBuilder[ClassDefinition]

  def buildClass: Class = Class(name, definitions.result, location)
  def buildInterface: Interface = Interface(name, definitions.result, location)

  override def evalMayHaveSideEffects = false
  override def typ = ClassBuilderRoot
  override def toUValue(core: Core) = inconvertible
  override def unboundNames = Set.empty

  override def evalType = None
  override protected def evaluate: EValue = this
  override def finalEval = this
}

object ClassRootType extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "new" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        if (args.count == 2) {
          MethodType.of(
            Seq(
              "name" -> String,
              // TODO: (builder: ClassBuilder): Void
              "builder" -> StaticType
            ),
            StaticType
          )
        } else {
          MethodType.of(
            Seq("builder" -> StaticType),
            StaticType
          )
        }
      }

      override def run(args: Arguments[EValue], location: Option[Location]) = {
        Lazy.selfReferencing[Class](self => {
          val (name, builderFn) =
            if (args.count == 2) {
              (
                Some(args.getEval[StringValue](1, "name")),
                args.getEval[EValue](2, "builder")
              )
            } else {
              (None, args.getEval[EValue](1, "builder"))
            }

          val builder = new ClassBuilder(name.map(_.value), LazyValue(self, location), location)

          val buildMethod = builderFn.typ.method("call")
            .getOrElse {
              throw EvalError("Object passed to Class.new needs to be callable", location)
            }

          buildMethod.call(
            Arguments.positional(builderFn, Seq(builder)),
            location
          )

          builder.buildClass
        }).resolve
      }
    }
  )
}

object ClassRoot extends StandardType {
  override def typ = ClassRootType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class ClassT(klass: photon.core.Class) extends StandardType {
  override val location = klass.location
  override def unboundNames = Set.empty
  override def typ = TypeRoot
  override def toUValue(core: Core) = inconvertible

  override val methods = Map(
    "new" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        MethodType.of(
          klass.definitions
            // TODO: This is not correct as a check for types
            .filter(d => d.value.evalType.getOrElse(d.value.typ) == TypeRoot)
            .map(d => d.name -> d.value.assertType),

          klass
        )

      override def run(args: Arguments[EValue], location: Option[Location]) =
        Object(klass, args.named, location)
    }
  )
}

case class Class(
  name: Option[String],
  definitions: Seq[ClassDefinition],
  location: Option[Location]
) extends StandardType {
  override def inspect = name.getOrElse(super.inspect)

  override lazy val typ = ClassT(this)
  override def unboundNames = definitions.flatMap(_.value.unboundNames).toSet

  override def toUValue(core: Core) = ???
//    CallValue(
//      "new",
//      Arguments.positional(ClassRoot, properties),
//      location
//    ).toUValue(core)

  override def finalEval = Class(
    name,
    definitions.map(
      d => ClassDefinition(d.name, d.value.finalEval, d.location)
    ),
    location
  )

  override val methods = definitions.map(methodForDefinition).toMap
  private def methodForDefinition(definition: ClassDefinition): (String, Method) = {
    // TODO: RunMode of method
    definition.name -> new Method {
      override def specialize(args: Arguments[EValue], location: Option[Location]) =
        definition.value.evaluated(EvalMode.CompileTimeOnly) match {
          case typ: Type => MethodType.of(Seq.empty, typ)
          case fn: FunctionValue => MethodType.of(
            fn.typ.params
              .map(param => param.name -> param.typ.assertType),
            fn.typ.returnType.assertType
          )
          case _ => throw EvalError(s"Invalid definition type $this, expected a type or a function", location)
        }

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.selfEval[Object]

        // TODO: This should set the EvalMode
        definition.value.evaluated match {
          case _: Type =>
            val value = self.properties.get(definition.name)

            value match {
              // TODO: This may call evaluated multiple times
              case Some(value) => value.evaluated
              case None => throw EvalError("There's no such property on the class instance", location)
            }

          case fn: FunctionValue =>
            val needsSelfArgument = fn.nameMap.contains("self")
            val hasSelfInArguments = args.named.contains("self")

            val methodArgs = Arguments(
              fn,
              positional = args.positional,
              named =
                if (needsSelfArgument && !hasSelfInArguments) {
                  args.named + ("self" -> self)
                } else {
                  args.named
                }
            )

            fn.typ.method("call")
              .getOrElse { throw EvalError("Cannot call function", location) }
              .call(methodArgs, location)

          case _ => throw EvalError(s"Invalid definition type $this, expected a type or a function", location)
        }
      }
    }
  }
}

case class Object(classValue: photon.core.Class, properties: Map[String, EValue], location: Option[Location]) extends EValue {
  override def typ = classValue
  override def evalType = None

  override def unboundNames = properties.values.flatMap(_.unboundNames).toSet

  // TODO: Can it have side-effects?
  //       If any of the not-yet-evaluated properties have side-effects when evaluating?
  override def evalMayHaveSideEffects = false
  override def toUValue(core: Core) = ???
//    CallValue(
//      "new",
//      Arguments.named(typ, properties),
//      location
//    ).toUValue(core)

  override protected def evaluate: EValue = this
  override def finalEval = Object(
    classValue,
    properties.view.mapValues(_.finalEval).toMap,
    location
  )
}

case class ClassDefinition(name: String, value: EValue, location: Option[Location]) {
  def toUValue(core: Core) = ???
  //    CallValue(
  //      "property",
  //      Arguments.positional(
  //        ClassRoot,
  //        Seq(name, propType)
  //      ),
  //      location
  //    ).toUValue(core)
}