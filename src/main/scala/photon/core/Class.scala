package photon.core
import photon.core.operations.{CallValue, FunctionValue}
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location}

object ClassRootType extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
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
        val evalArgs = args.positional.map(_.evaluated)
        val properties = evalArgs
          .filter(_.isInstanceOf[PropertyValue])
          .map(_.assert[PropertyValue])

        val methods = evalArgs
          .filter(_.isInstanceOf[MethodValue])
          .map(_.assert[MethodValue])

        photon.core.Class(properties, methods, location)
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
    },

    "method" -> new Method {
      override val traits = Set(MethodTrait.CompileTime)
      override def typeCheck(args: Arguments[EValue]) = MethodType
      override def call(args: Arguments[EValue], location: Option[Location]) =
        MethodValue(
          args.positional.head.evalAssert[StringValue],
          args.positional(1).evalAssert[FunctionValue],
          location
        )
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
case class Class(
  properties: Seq[PropertyValue],
  instanceMethods: Seq[MethodValue],
  location: Option[Location]
) extends StandardType {
  override lazy val typ = ClassT(this)
  override def unboundNames = properties.flatMap(_.unboundNames).toSet

  override def toUValue(core: Core) =
    CallValue(
      "new",
      Arguments.positional(ClassRoot, properties),
      location
    ).toUValue(core)

  override val methods = properties.map { prop =>
    prop.name.value -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = prop.propType.evalAssert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[Object]
        val propName = prop.name.value
        val propValue = self.properties.get(propName)

        propValue match {
          case Some(value) => value
          case None => throw EvalError("There's no such property on the class instance", location)
        }
      }
    }
  }.toMap[String, Method] ++ instanceMethods.map { method =>
    method.name.value -> new Method {
      override val traits = Set(MethodTrait.CompileTime, MethodTrait.RunTime)
      override def typeCheck(args: Arguments[EValue]) = method.fn.typ.returnType.evalAssert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val self = args.self.evalAssert[Object]
        val fn = method.fn

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
      }
    }
  }
}

case class Object(classValue: EValue, properties: Map[String, EValue], location: Option[Location]) extends EValue {
  override def typ = classValue.evalAssert[photon.core.Class]
  override def evalType = None

  // TODO: Not sure if this including the classValue is 100% correct
  override def unboundNames = classValue.unboundNames ++ properties.values.flatMap(_.unboundNames).toSet

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
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}
case class PropertyValue(name: StringValue, propType: EValue, location: Option[Location]) extends EValue {
  override def typ = Property
  override def unboundNames = propType.unboundNames
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

object MethodType extends StandardType {
  override def typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = inconvertible
  override val methods = Map.empty
}
case class MethodValue(name: StringValue, fn: FunctionValue, location: Option[Location]) extends EValue {
  override def typ = Property
  override def unboundNames = fn.unboundNames
  override def evalType = None
  override def evalMayHaveSideEffects = false
  override def toUValue(core: Core) =
    CallValue(
      "method",
      Arguments.positional(
        ClassRoot,
        Seq(name, fn)
      ),
      location
    ).toUValue(core)

  override protected def evaluate: EValue = this
}