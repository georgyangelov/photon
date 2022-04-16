package photon.frontend

import photon.base.Location

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
        case ASTParameter(name, Some(typeValue), _) => s"(param $name ${typeValue.inspect})"
        case ASTParameter(name, None, _) => s"(param $name)"
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
}

case class ASTParameter(name: String, typeValue: Option[ASTValue], location: Option[Location])

case class ASTArguments(positional: Seq[ASTValue], named: Map[String, ASTValue])
object ASTArguments {
  val empty: ASTArguments = ASTArguments(Seq.empty, Map.empty)

  def positional(values: Seq[ASTValue]) = ASTArguments(values, named = Map.empty)
}