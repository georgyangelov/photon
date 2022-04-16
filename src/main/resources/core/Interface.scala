package photon.core
import photon.ArgumentExtensions._
import photon.interpreter.EvalError
import photon.lib.Lazy
import photon.{Arguments, CompileTimeOnlyMethod, DefaultMethod, EValue, Location, MethodType}

// TODO: Add helpers for companion objects
object InterfaceRootType extends StandardType {
  override val location = None
  override def typ = TypeRoot

  override def unboundNames = Set.empty
  override def toUValue(core: Core) = inconvertible

  override val methods = Map(
    "new" -> new CompileTimeOnlyMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        if (args.count == 2) {
          MethodType.of(
            Seq(
              "name" -> String,
              // TODO: (builder: ClassBuilder): void
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

      override def run(args: Arguments[EValue], location: Option[Location]): EValue = {
        Lazy.selfReferencing[Interface](self => {
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
              throw EvalError("Object passed to Interface.new needs to be callable", location)
            }

          buildMethod.call(
            Arguments.positional(builderFn, Seq(builder)),
            location
          )

          builder.buildInterface
        }).resolve
      }
    }
  )
}

object InterfaceRoot extends StandardType {
  override def typ = InterfaceRootType
  override def unboundNames = Set.empty
  override val location = None
  override def toUValue(core: Core) = core.referenceTo(this, location)
  override val methods = Map.empty
}

case class InterfaceT(interface: photon.core.Interface) extends StandardType {
  override val location = interface.location
  override def unboundNames = Set.empty
  override def typ = TypeRoot
  override def toUValue(core: Core) = inconvertible

  override val methods = Map(
    "from" -> new DefaultMethod {
      override def specialize(args: Arguments[EValue], location: Option[Location]) = {
        val value = args.get(1, "value")
        val valueType = value.evalType.getOrElse(value.typ)

        interface.definitions.foreach { definition =>
          valueType.method(definition.name) match {
            case Some(method) =>
              method.specialize()

            case None => throw EvalError(
              s"Method ${definition.name} not present on ${value.inspect}, required by ${interface.inspect}",
              location
            )
          }
        }

        MethodType.of(
          Seq("value" -> StaticType),
          interface
        )
      }

      override def run(args: Arguments[EValue], location: Option[Location]) =
        InterfaceValue(interface, args.positional.head, location)
    }
  )
}

case class Interface(
  name: Option[String],
  definitions: Seq[ClassDefinition],
  location: Option[Location]
) extends StandardType {
  override def inspect = name.getOrElse(super.inspect)
  override def typ = InterfaceT(this)

  override def unboundNames = definitions.flatMap(_.value.unboundNames).toSet
  override def toUValue(core: Core) = ???

  override def finalEval = Class(
    name,
    definitions.map(
      d => ClassDefinition(d.name, d.value.finalEval, d.location)
    ),
    location
  )

  override val methods = Map(
    // Method for each definition

  )
}

case class InterfaceValue(interface: Interface, value: EValue, location: Option[Location]) extends EValue {
  override def typ = interface
  override def unboundNames = value.unboundNames

  // TODO: Interface.from(value)
  override def toUValue(core: Core) = ???

  override def evalType = None
  override protected def evaluate: EValue = this

//  override def finalEval = Object(
//    interface,
//    properties.view.mapValues(_.finalEval).toMap,
//    location
//  )

  override def finalEval = ???
  override def evalMayHaveSideEffects = false
}
