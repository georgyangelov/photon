package photon.frontend

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

    case _ => throw new Exception(s"Cannot unparse value ${value.inspectAST}")
  }

//  def unparse(block: ASTBlock): String = {
//    val lastIndex = block.values.size - 1
//
//    block.values.zipWithIndex.map { case (value, index) =>
//      unparse(value, expectSingleValue = index != lastIndex)
//    }.mkString("; ")
//  }

  def unparse(parameter: ASTParameter): String = {
    parameter match {
      case ASTParameter(name, Some(typeValue), _) => s"$name: ${unparse(typeValue, expectSingleValue = false)}"
      case ASTParameter(name, None, _) => name
    }
  }

  def unparse(arguments: ASTArguments): String = {
    val positionalArguments = arguments.positional.map(unparse(_, expectSingleValue = false))
    val namedArguments = arguments.named.map { case (name, value) => s"${name} = ${unparse(value, expectSingleValue = false)}" }

    (positionalArguments ++ namedArguments).mkString(", ")
  }
}
