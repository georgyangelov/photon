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
    override val unboundNames =
      arguments.self.unboundNames ++
        arguments.positional.map(_.unboundNames).unionSets ++
        arguments.named.values.map(_.unboundNames).unionSets
  }
}

sealed trait UPattern {
  val unboundNames: Set[VariableName]
  val definitions: Set[String]
}
object UPattern {
  case class SpecificValue(value: UValue) extends UPattern {
    override val unboundNames = value.unboundNames
    override val definitions = Set.empty
  }

  case class Val(name: String) extends UPattern {
    override val unboundNames = Set.empty
    override val definitions = Set(name)
  }
}

class UFunction(
  val params: Seq[UParameter],
  val nameMap: Map[String, VariableName],
  val body: UValue,
  val returnType: Option[UValue]
) {
  val unboundNames = params.map(_.unboundNames).unionSets ++ body.unboundNames -- nameMap.values
}

// TODO: Type here should be optional as it may rely on the usage
case class UParameter(outName: String, inName: String, typ: UPattern, location: Option[Location]) {
  val unboundNames = typ.unboundNames
}