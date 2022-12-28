package photonscala.frontend

sealed abstract class ASTValue {
  def location: Option[Location]
  def inspect: String
}

object ASTValue {
  case class Boolean(value: java.lang.Boolean, location: Option[Location]) extends ASTValue {
    override def inspect = value.toString
  }

  case class Int(value: java.lang.Integer, location: Option[Location]) extends ASTValue {
    override def inspect = value.toString
  }

  case class Float(value: java.lang.Double, location: Option[Location]) extends ASTValue {
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

  case class FunctionType(
    params: Seq[ASTTypeParameter],
    returnType: ASTValue,
    location: Option[Location]
  ) extends ASTValue {
    override def inspect = {
      val paramsAST = params
        .map { param => s"(param ${param.name} ${param.typ.inspect})" }
        .mkString(" ")

      s"(Function [$paramsAST] $returnType)"
    }
  }
}

case class ArgumentsWithoutSelf[+T](positional: Seq[T], named: Map[String, T]) {
  def argValues = positional ++ named.values

  def map[R](f: T => R) = ArgumentsWithoutSelf(
    positional.map(f),
    named.view.mapValues(f).toMap
  )
}
object ArgumentsWithoutSelf {
  def empty[T]: ArgumentsWithoutSelf[T] = ArgumentsWithoutSelf(Seq.empty, Map.empty)

  def positional[T](values: Seq[T]) =
    ArgumentsWithoutSelf[T](
      positional = values,
      named = Map.empty
    )

  def named[T](values: Map[String, T]) =
    ArgumentsWithoutSelf[T](
      positional = Seq.empty,
      named = values
    )
}

sealed trait Pattern {
  def location: Option[Location]
  def inspect: java.lang.String
}
object Pattern {
  case class SpecificValue(value: ASTValue) extends Pattern {
    override def location = value.location
    override def inspect = value.inspect
  }

  case class Binding(name: java.lang.String, location: Option[Location]) extends Pattern {
    override def inspect = s"(val $name)"
  }

  case class Call(
    target: ASTValue,
    name: java.lang.String,
    arguments: ArgumentsWithoutSelf[Pattern],
    mayBeVarCall: scala.Boolean,
    location: Option[Location]
  ) extends Pattern {
    override def inspect = {
      val positionalArguments = arguments.positional.map(_.inspect)
      val namedArguments = arguments.named.map { case (name, value) => s"(param $name ${value.inspect})" }
      val argumentStrings = positionalArguments ++ namedArguments

      if (argumentStrings.isEmpty) {
        s"<$name ${target.inspect}>"
      } else {
        s"<$name ${target.inspect} ${argumentStrings.mkString(" ")}>"
      }
    }
  }

  case class FunctionType(
    params: Seq[ASTPatternParameter],
    returnType: Pattern,
    location: Option[Location]
  ) extends Pattern {
    override def inspect = {
      val paramsAST = params
        .map { param => s"(param ${param.name} ${param.typ.inspect})" }
        .mkString(" ")

      s"(Function [$paramsAST] ${returnType.inspect})"
    }
  }
}

sealed abstract class ASTValueOrPattern {
  def location: Option[Location]
}
object ASTValueOrPattern {
  case class Value(value: ASTValue) extends ASTValueOrPattern {
    def location = value.location
  }

  case class Pattern(pattern: frontend.Pattern) extends ASTValueOrPattern {
    def location = pattern.location
  }
}

case class ASTParameter(
  outName: String,
  inName: String,
  typePattern: Option[Pattern],
  location: Option[Location]
)

case class ASTTypeParameter(
  name: String,
  typ: ASTValue,
  location: Option[Location]
)

case class ASTPatternParameter(
  name: String,
  typ: Pattern,
  location: Option[Location]
)

case class ASTArguments(positional: Seq[ASTValue], named: Map[String, ASTValue])
object ASTArguments {
  val empty: ASTArguments = ASTArguments(Seq.empty, Map.empty)

  def positional(values: Seq[ASTValue]) = ASTArguments(values, named = Map.empty)
}