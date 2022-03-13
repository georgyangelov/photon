package photon.core
import photon.core.operations.FunctionValue
import photon.interpreter.EvalError
import photon.lib.Lazy
import photon.{Arguments, EValue, Location}

object ClassBuilderRoot extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map(
    "define" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly

      override def typeCheck(args: Arguments[EValue]) = photon.core.String

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[ClassBuilder]
        val name = args.positional.head.evalAssert[StringValue]
        val definition = args.positional(1)

        self.definitions.addOne(ClassDefinition(name.value, definition, location))

        name
      }
    },

    "classType" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly

      override def typeCheck(args: Arguments[EValue]) = TypeRoot

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[ClassBuilder]

        self.classRef.evaluated
      }
    }
  )
}

class ClassBuilder(
  val className: Option[String],
  val classRef: EValue,
  val location: Option[Location]
) extends EValue {
  val definitions = Seq.newBuilder[ClassDefinition]

  def build: Class = Class(className, definitions.result, location)

  override def evalMayHaveSideEffects = false
  override def typ = ClassBuilderRoot
  override def toUValue(core: Core) = inconvertible
  override def unboundNames = Set.empty

  override def evalType = None
  override protected def evaluate: EValue = this
}

object ClassRootType extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "new" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly

      // TODO: CompileTimeOnlyMethod class which does not have separate typeCheck and call?
      // TODO: Location for the typeCheck method
      override def typeCheck(args: Arguments[EValue]) = StaticType

      override def call(args: Arguments[EValue], location: Option[Location]) = buildClass(args, location)

      def buildClass(args: Arguments[EValue], location: Option[Location]) = {
        Lazy.selfReferencing[Class]((self) => {
          val (name, builderFn) =
            if (args.positional.size == 2) {
              (
                Some(args.positional.head.evalAssert[StringValue]),
                args.positional(1).evaluated
              )
            } else {
              (None, args.positional.head.evaluated)
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

          builder.build
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
    "new" -> new Method {
      override val runMode = MethodRunMode.Default

      // TODO: Verify property types
      override def typeCheck(args: Arguments[EValue]) = klass

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        // TODO: Verify properties

        Object(klass, args.named, location)
      }
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

  override val methods = definitions.map(methodForDefinition).toMap

//  override val methods = (valueDefs.map(methodForValue) ++ methodDefs.map(methodForFn)).toMap

  private def methodForDefinition(definition: ClassDefinition): (String, Method) = {
    definition.name -> new Method {
      override val runMode = MethodRunMode.Default
      override def typeCheck(args: Arguments[EValue]) = {
        definition.value.evaluated match {
          case typ: Type => typ
          case fn: FunctionValue => fn.typ.returnType.evalAssert[Type]
          case _ => throw EvalError(s"Invalid definition type $this, expected a type or a function", location)
        }
      }

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[Object]

        definition.value.evaluated match {
          case _: Type =>
            val value = self.properties.get(definition.name)

            value match {
              case Some(value) => value
              case None => throw EvalError("There's no such property on the class instance", location)
            }

          case fn: FunctionValue =>
            val self = args.self.evalAssert[Object]

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