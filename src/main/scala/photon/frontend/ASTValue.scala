package photon.frontend

import photon.base.{Arguments, ArgumentsWithoutSelf, Location}

sealed abstract class ASTValue {
  def location: Option[Location]

  override def toString: String = Unparser.unparse(this)

  def inspect: String
}

object ASTValue {
  case class Boolean(value: scala.Boolean, location: Option[Location]) extends ASTValue {
    override def inspect = value.toString
  }

  case class Int(value: scala.Int, location: Option[Location]) extends ASTValue {
    override def inspect = value.toString
  }

  case class Float(value: scala.Double, location: Option[Location]) extends ASTValue {
    override def inspect = value.toString
  }

  case class String(value: java.lang.String, location: Option[Location]) extends ASTValue {
    override def inspect = {
      val escapedString = value
        .replaceAll("([\"\\\\])", "\\\\$1")
        .replaceAllLiterally("\n", "\\n")

      '"' + escapedString + '"'
    }
  }

  case class Block(values: Seq[ASTValue], location: Option[Location]) extends ASTValue {
    override def inspect =
      if (values.nonEmpty) {
        s"{ ${values.map(_.inspect).mkString(" ")} }"
      } else { "{}" }
  }

  case class Function(
    params: Seq[ASTParameter],
    body: ASTValue,
    returnType: Option[ASTValue],
    location: Option[Location]
  ) extends ASTValue {
    override def inspect = {
      val bodyAST = body.inspect
      val paramsAST = params.map {
        case ASTParameter(outName, inName, Some(typeValue), _) =>
          if (outName == inName)
            s"(param $outName ${typeValue.inspect})"
          else
            s"(param $outName $inName ${typeValue.inspect})"

        case ASTParameter(outName, inName, None, _) =>
          if (outName == inName)
            s"(param $outName)"
          else
            s"(param $outName $inName)"
      }.mkString(" ")

      val returnTypeAST = returnType.map { t => s"$t " }.getOrElse("")

      s"(lambda [$paramsAST] $returnTypeAST$bodyAST)"
    }
  }

  case class Call(
    target: ASTValue,
    name: java.lang.String,
    arguments: ASTArguments,
    mayBeVarCall: scala.Boolean,
    location: Option[Location]
  ) extends ASTValue {
    override def inspect = {
      val positionalArguments = arguments.positional.map(_.inspect)
      val namedArguments = arguments.named.map { case (name, value) => s"(param $name ${value.inspect})" }
      val argumentStrings = positionalArguments ++ namedArguments

      if (argumentStrings.isEmpty) {
        s"($name ${target.inspect})"
      } else {
        s"($name ${target.inspect} ${argumentStrings.mkString(" ")})"
      }
    }
  }

  case class NameReference(name: java.lang.String, location: Option[Location]) extends ASTValue {
    override def inspect = name
  }

  case class Let(name: java.lang.String, value: ASTValue, block: ASTValue, location: Option[Location]) extends ASTValue {
    override def inspect = s"(let $name ${value.inspect} ${block.inspect})"
  }

  sealed trait Pattern extends ASTValue {
    def inspect: java.lang.String
  }
  object Pattern {
    case class SpecificValue(value: ASTValue, location: Option[Location]) extends Pattern {
      override def inspect = value.inspect
    }

    case class Binding(name: java.lang.String, location: Option[Location]) extends Pattern {
      override def inspect = s"(val $name)"
    }

    case class Call(
      target: ASTValue,
      name: java.lang.String,
      args: ArgumentsWithoutSelf[Pattern],
      mayBeVarCall: scala.Boolean,
      location: Option[Location]
    ) extends Pattern {
      override def inspect = {
        val positionalArguments = args.positional.map(_.inspect)
        val namedArguments = args.named.map { case (name, value) => s"(param $name ${value.inspect})" }
        val argumentStrings = positionalArguments ++ namedArguments

        if (argumentStrings.isEmpty) {
          s"<$name ${target.inspect}>"
        } else {
          s"<$name ${target.inspect} ${argumentStrings.mkString(" ")}>"
        }
      }
    }
  }
}

case class ASTParameter(
  outName: String,
  inName: String,
  typePattern: Option[ASTValue.Pattern],
  location: Option[Location]
)

case class ASTArguments(positional: Seq[ASTValue], named: Map[String, ASTValue])
object ASTArguments {
  val empty: ASTArguments = ASTArguments(Seq.empty, Map.empty)

  def positional(values: Seq[ASTValue]) = ASTArguments(values, named = Map.empty)
}