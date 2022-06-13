package photon.frontend

import photon.base._
import photon.lib.ScalaExtensions.IterableSetExtensions

sealed trait UValue {
  val location: Option[Location]
  val unboundNames: Set[VarName]

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
    name: VarName,
    value: UValue,
    block: UValue,
    location: Option[Location]
  ) extends UValue {
    override val unboundNames = (value.unboundNames ++ block.unboundNames) - name
  }

  case class Reference(
    name: VarName,
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
  val definitions: Set[VarName]
}
object UPattern {
  case class SpecificValue(value: UValue, location: Option[Location]) extends UPattern {
    override val definitions = Set.empty
    override val unboundNames = value.unboundNames
  }

  case class Binding(name: VarName, location: Option[Location]) extends UPattern {
    override val definitions = Set(name)
    override val unboundNames = Set.empty
  }

  case class Call(target: UValue, name: String, args: ArgumentsWithoutSelf[UPattern], location: Option[Location]) extends UPattern {
    override val definitions = args.argValues.flatMap(_.definitions).toSet

    // TODO: What about this: `List.of(a, val a, a)`, should the first `a` be
    //       unbound or equal to the second one? It should most probably be unbound.
    override val unboundNames = args.argValues.flatMap(_.unboundNames).toSet -- definitions
  }
}

class UFunction(
  val params: Seq[UParameter],
  val nameMap: Map[String, VarName],
  val body: UValue,
  val returnType: Option[UValue]
) {
  val unboundNames = params.flatMap(_.unboundNames).toSet ++ body.unboundNames -- nameMap.values
}

// TODO: Type here should be optional as it may rely on the usage
// TODO: Should the `inName` be a VarName? We won't need the nameMap (probably)
case class UParameter(outName: String, inName: String, typ: UPattern, location: Option[Location]) {
  val unboundNames = typ.unboundNames
}