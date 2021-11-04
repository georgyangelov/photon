package photon.core

import photon.New.TypeObject
import photon.interpreter.{CallContext, EvalError}
import photon.{AnyType, ArgumentType, Arguments, BoundValue, Location, MethodType, New, PureValue, RealValue, TypeType}
import photon.core.Conversions._

// Class.type
object ClassTypeTypeType extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    // Class.new
    "new" -> new New.CompileTimeOnlyMethod {
      override def methodType(argTypes: Arguments[TypeObject]) = MethodType(
        name = "new",
        arguments = Seq(
          ArgumentType("fields", ListType),
          ArgumentType("instanceMethods", ListType)
        ),
        returns = AnyType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val fields = args.getNative[List](Parameter(1, "fields"))
          .values
          .map(_.asNative[FieldObject])

        val instanceMethodObjects = args.getNative[List](Parameter(2, "instanceMethods"))
          .values
          .map(_.asNative[MethodObject])

        val instanceMethods = instanceMethodObjects
          .map(native => native.name -> native.function.typeObject.get.instanceMethod("call").get)
          .toMap

        val classType = ClassType(fields, instanceMethods)

        PureValue.Native(classType, location)
      }
    },

    "field" -> new New.CompileTimeOnlyMethod {
      override def methodType(argTypes: Arguments[TypeObject]) = MethodType(
        name = "field",
        arguments = Seq(
          ArgumentType("name", StringType),
          ArgumentType("type", TypeType)
        ),
        returns = FieldType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val name = args.getString(Parameter(1, "name"))
        val typeObject = args.getNative[New.TypeObject](Parameter(2, "type"))

        PureValue.Native(FieldObject(name, typeObject), location)
      }
    },

    "method" -> new New.CompileTimeOnlyMethod {
      override def methodType(argTypes: Arguments[TypeObject]) = MethodType(
        name = "method",
        arguments = Seq(
          ArgumentType("name", StringType),
          ArgumentType("function", AnyType)
        ),
        returns = MethodObjectType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val name = args.getString(Parameter(1, "name"))
        val function = args.getFunction(Parameter(2, "function"))

        PureValue.Native(MethodObject(name, function), location)
      }
    }
  )
}

// Class
object ClassObject extends New.NativeObject(ClassTypeTypeType)

// Person.type
case class ClassTypeType(classType: ClassType) extends New.TypeObject {
  override val typeObject = TypeType

  override val instanceMethods = Map(
    // Person.new
    "new" -> new New.StandardMethod {
      override def methodType(argTypes: Arguments[TypeObject]) = MethodType(
        name = "new",
        arguments = classType.fields.map(field => ArgumentType(field.name, field.typeObject)),
        returns = classType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
        BoundValue.Object(args.named, context.callScope, Some(classType), location)
    }
  )
}

// Person
case class ClassType(
  fields: Seq[FieldObject],
  private val _instanceMethods: Map[String, NativeMethod]
) extends New.TypeObject {
  override val typeObject = ClassTypeType(this)

  override val instanceMethods = fields.map(field => field.name -> new New.StandardMethod {
    override def methodType(argTypes: Arguments[TypeObject]) = MethodType(
      name = field.name,
      arguments = Seq.empty,
      returns = field.fieldType
    )

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      val self = args.getObject(Parameter(0, "self"))

      self.values.getOrElse(field.name, throw EvalError(s"Object $self needs to contain a field '${field.name}'", location))
    }
  }).toMap ++ _instanceMethods
}

object FieldType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}
case class FieldObject(name: String, fieldType: TypeObject) extends New.NativeObject(FieldType)

object MethodObjectType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}
case class MethodObject(name: String, function: BoundValue.Function) extends New.NativeObject(MethodObjectType)