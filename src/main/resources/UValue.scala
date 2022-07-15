package photon

import photon.frontend.Unparser
import photon.lib.ObjectId
import photon.lib.ScalaExtensions._


sealed trait UValue {
  val location: Option[Location]
  val unboundNames: Set[VariableName]

  override def toString = Unparser.unparse(ValueToAST.transform(this))
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
    fn: photon.UFunction,
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

class UFunction(
  val params: Seq[UParameter],
  val nameMap: Map[String, VariableName],
  val body: UValue,
  val returnType: Option[UValue]
) extends Equals {
  val unboundNames = body.unboundNames -- nameMap.values

  // TODO: Do I need this?
  val objectId = ObjectId()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[UFunction]
  override def equals(that: Any): Boolean = {
    that match {
      case other: UFunction => this.objectId == other.objectId
      case _ => false
    }
  }
  override def hashCode(): Int = objectId.hashCode
}

// TODO: Type here should be optional as it may rely on the usage
case class UParameter(name: String, typ: UValue, location: Option[Location])