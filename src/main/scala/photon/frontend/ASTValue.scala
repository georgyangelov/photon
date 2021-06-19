package photon.frontend

import photon.{Location, Unparser}

import scala.collection.Map

sealed abstract class ASTValue {
  def location: Option[Location]

  override def toString: String = Unparser.unparse(this)

  def inspectAST: String = {
    this match {
      case ASTValue.Boolean(value, _) => value.toString
      case ASTValue.Int(value, _) => value.toString
      case ASTValue.Float(value, _) => value.toString
      case ASTValue.String(value, _) =>
        val escapedString = value
          .replaceAll("([\"\\\\])", "\\\\$1")
          .replaceAllLiterally("\n", "\\n")

        '"' + escapedString + '"'

      case ASTValue.Call(target, name, arguments, _, _) =>
        val positionalArguments = arguments.positional.map(_.inspectAST)
        val namedArguments = arguments.named.map { case (name, value) => s"(param $name ${value.inspectAST})" }
        val argumentStrings = positionalArguments ++ namedArguments

        if (argumentStrings.isEmpty) {
          s"($name ${target.inspectAST})"
        } else {
          s"($name ${target.inspectAST} ${argumentStrings.mkString(" ")})"
        }

      case ASTValue.NameReference(name, _) => name

      case ASTValue.Let(name, value, block, _) => s"(let $name ${value.inspectAST} ${block.inspectAST})"

      case ASTValue.Function(params, body, _) =>
        val bodyAST = body.inspectAST
        val paramsAST = params.map {
          case ASTParameter(name, Some(typeValue)) => s"(param $name ${typeValue.inspectAST})"
          case ASTParameter(name, None) => s"(param $name)"
        }.mkString(" ")

        s"(lambda [$paramsAST] $bodyAST)"
    }
  }
}

object ASTValue {
  case class Boolean(value: scala.Boolean, location: Option[Location]) extends ASTValue
  case class Int(value: scala.Int, location: Option[Location]) extends ASTValue
  case class Float(value: scala.Double, location: Option[Location]) extends ASTValue
  case class String(value: java.lang.String, location: Option[Location]) extends ASTValue

  case class Function(params: Seq[ASTParameter], body: ASTBlock, location: Option[Location]) extends ASTValue

  case class Call(
    target: ASTValue,
    name: java.lang.String,
    arguments: ASTArguments,
    mayBeVarCall: scala.Boolean,
    location: Option[Location]
  ) extends ASTValue
  case class NameReference(name: java.lang.String, location: Option[Location]) extends ASTValue
  case class Let(name: java.lang.String, value: ASTValue, block: ASTBlock, location: Option[Location]) extends ASTValue
}

case class ASTParameter(name: String, typeValue: Option[ASTValue])

case class ASTArguments(positional: Seq[ASTValue], named: Map[String, ASTValue])
object ASTArguments {
  val empty: ASTArguments = ASTArguments(Seq.empty, Map.empty)
}

case class ASTBlock(values: Seq[ASTValue]) {
  override def toString: String = Unparser.unparse(this)

  def inspectAST = {
    if (values.nonEmpty) {
      s"{ ${values.map(_.inspectAST).mkString(" ")} }"
    } else { "{}" }
  }
}
