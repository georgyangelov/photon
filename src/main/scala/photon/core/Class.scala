package photon.core
import photon.core.operations.CallValue
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location}

object ClassRootType extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map(
    "new" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)

      // TODO: CompileTimeOnlyMethod class which does not have separate typeCheck and call?
      // TODO: Location for the typeCheck method
      override def typeCheck(args: Arguments[EValue]) = {
        val klass = buildClass(args, None)

        klass.evalType.getOrElse(klass.typ)
      }

      override def call(args: Arguments[EValue], location: Option[Location]) = buildClass(args, location)

      def buildClass(args: Arguments[EValue], location: Option[Location]) = {
        val properties = args.positional
          .map(_.evaluated)
          .filter(_.isInstanceOf[PropertyValue])
          .map(_.assert[PropertyValue])

        photon.core.Class(properties, location)
      }
    },

    "property" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(args: Arguments[EValue]) = Property
      override def call(args: Arguments[EValue], location: Option[Location]) =
        PropertyValue(
          args.positional.head.evalAssert[StringValue],
          args.positional(1),
          location
        )
    }
  )
}

object ClassRoot extends StandardType {
  override def typ = ClassRootType
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class ClassT(klass: photon.core.Class) extends StandardType {
  override val location = klass.location
  override def typ = TypeRoot
  override def toUValue(core: Core) = inconvertible

  override val methods = Map(
    "new" -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)

      // TODO: Verify property types
      override def typeCheck(args: Arguments[EValue]) = klass

      override def call(args: Arguments[EValue], location: Option[Location]) = {
        // TODO: Verify properties

        Object(klass, args.named, location)
      }
    }
  )
}
case class Class(properties: Seq[PropertyValue], location: Option[Location]) extends StandardType {
  override lazy val typ = ClassT(this)

  override def toUValue(core: Core) =
    CallValue(
      "new",
      Arguments.positional(ClassRoot, properties),
      location
    ).toUValue(core)

  override val methods = properties.map { prop =>
    prop.name.value -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = ???
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[Object]
        val propName = prop.name.value
        val propValue = self.properties.get(propName)

        propValue match {
          case Some(value) => value
          case None => throw EvalError("Methods are not yet supported on classes", location)
        }
      }
    }
  }.toMap
}

case class Object(typ: photon.core.Class, properties: Map[String, EValue], location: Option[Location]) extends EValue {
  override def evalType = None

  // TODO: Can it have side-effects?
  //       If any of the not-yet-evaluated properties have side-effects when evaluating?
  override def evalMayHaveSideEffects = false
  override def toUValue(core: Core) =
    CallValue(
      "new",
      Arguments.named(typ, properties),
      location
    ).toUValue(core)

  override protected def evaluate: EValue = this
}

object Property extends StandardType {
  override def typ = TypeRoot
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}
case class PropertyValue(name: StringValue, propType: EValue, location: Option[Location]) extends EValue {
  override def typ = Property
  override def evalType = None
  override def evalMayHaveSideEffects = false
  override def toUValue(core: Core) =
    CallValue(
      "property",
      Arguments.positional(
        ClassRoot,
        Seq(name, propType)
      ),
      location
    ).toUValue(core)

  override protected def evaluate: EValue = this
}