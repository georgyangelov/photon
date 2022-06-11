package photon.frontend

import photon.base._
import photon.lib.ScalaExtensions.IterableSetExtensions


sealed trait UValue {
  val location: Option[Location]
  val unboundNames: Set[VariableName]

  override def toString = Unparser.unparse(UValueToAST.transform(this))
}

object ULiteral {
  case class Nothing(location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Boolean(value: scala.Boolean, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Int(value: scala.Int, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class Float(value: scala.Double, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
  case class String(value: java.lang.String, location: Option[Location]) extends UValue {
    override val unboundNames = Set.empty
  }
}

object UOperation {
  case class Block(
    values: Seq[UValue],
    location: Option[Location]
  ) extends UValue {
    override lazy val unboundNames = values.map(_.unboundNames).unionSets
  }

  case class Let(
    name: VariableName,
    value: UValue,
    block: UValue,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = (value.unboundNames ++ block.unboundNames) - name
  }

  case class Reference(
    name: VariableName,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = Set(name)
  }

  case class Function(
    fn: UFunction,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = fn.unboundNames
  }

  case class Call(
    name: String,
    arguments: Arguments[UValue],
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = arguments.values.flatMap(_.unboundNames).toSet
  }
}

sealed trait UPattern extends UValue {
  val definitions: Set[VariableName]
}
object UPattern {
  case class SpecificValue(value: UValue, location: Option[Location]) extends UPattern {
    override val unboundNames = value.unboundNames
    override val definitions = Set.empty
  }

  case class Binding(name: VariableName, location: Option[Location]) extends UPattern {
    override val unboundNames = Set.empty
    override val definitions = Set(name)
  }

  case class Call(name: String, args: Arguments[UValue], location: Option[Location]) extends UPattern {
    override val unboundNames = args.values.flatMap(_.unboundNames).toSet
    override val definitions = args.values.flatMap(_.unboundNames).toSet
  }
}

class UFunction(
  val params: Seq[UParameter],
  val nameMap: Map[String, VariableName],
  val body: UValue,
  val returnType: Option[UValue]
) {
  val unboundNames = params.flatMap(_.unboundNames).toSet ++ body.unboundNames -- nameMap.values
}

// TODO: Type here should be optional as it may rely on the usage
case class UParameter(outName: String, inName: String, typ: UPattern, location: Option[Location]) {
  val unboundNames = typ.unboundNames
}