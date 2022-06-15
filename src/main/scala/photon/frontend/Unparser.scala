package photon.frontend

import photon.base.ArgumentsWithoutSelf

object Unparser {
  def unparse(value: ASTValue): String = unparse(value, expectSingleValue = true)

  private def unparse(value: ASTValue, expectSingleValue: Boolean): String = value match {
    case ASTValue.Boolean(value, _) => value.toString
    case ASTValue.Int(value, _) => value.toString
    case ASTValue.Float(value, _) => value.toString
    case ASTValue.String(value, _) =>
      "\"" + value.replace("\n", "\\n").replace("\"", "\\\"") + "\""

    case ASTValue.Function(params, body, returnType, _) =>
      val arguments = params.map(unparse).mkString(", ")
      val returns = returnType.map(unparse).map { t => s": $t" }.getOrElse("")

      s"($arguments)$returns { ${unparse(body)} }"

    case ASTValue.Call(t, name, arguments, _, _) =>
      val target = unparse(t, expectSingleValue = true)

      s"${if (target == "self") "" else s"$target."}$name(${unparse(arguments)})"

    case ASTValue.NameReference(name, _) => name

    case ASTValue.Let(name, value, block, _) =>
      val letString = s"val $name = ${unparse(value, expectSingleValue = true)}; ${unparse(block)}"

      if (expectSingleValue) {
        s"($letString)"
      } else {
        letString
      }

    case ASTValue.Block(values, _) =>
      val lastIndex = values.size - 1
      val blockString = values.zipWithIndex.map { case (value, index) =>
        unparse(value, expectSingleValue = index != lastIndex)
      }.mkString("; ")

      if (expectSingleValue) {
        s"($blockString)"
      } else {
        blockString
      }

    case ASTValue.Pattern.SpecificValue(value) => unparse(value)
    case ASTValue.Pattern.Binding(name, _) => s"val $name"
    case ASTValue.Pattern.Call(t, name, arguments, _, _) =>
      val target = unparse(t, expectSingleValue = true)

      s"${if (target == "self") "" else s"$target."}$name(${unparse(arguments)})"

    case _ => throw new Exception(s"Cannot unparse value ${value.inspect}")
  }

  def unparse(parameter: ASTParameter): String = {
    parameter match {
      case ASTParameter(outName, inName, Some(typ), _) if outName == inName => s"$outName: ${unparse(typ)}"
      case ASTParameter(outName, inName, Some(typ), _) => s"$outName as $inName: ${unparse(typ)}"
      case ASTParameter(outName, inName, None, _) if outName == inName => outName
      case ASTParameter(outName, inName, None, _) => s"$outName as $inName"
    }
  }

  def unparse(args: ArgumentsWithoutSelf[ASTValue]): String = {
    val positionalArguments = args.positional.map(unparse(_, expectSingleValue = false))
    val namedArguments = args.named.map { case (name, value) => s"$name = ${unparse(value, expectSingleValue = false)}" }

    (positionalArguments ++ namedArguments).mkString(", ")
  }

  def unparse(arguments: ASTArguments): String = {
    val positionalArguments = arguments.positional.map(unparse(_, expectSingleValue = false))
    val namedArguments = arguments.named.map { case (name, value) => s"$name = ${unparse(value, expectSingleValue = false)}" }

    (positionalArguments ++ namedArguments).mkString(", ")
  }
}
