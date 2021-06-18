package photon

object Unparser {
  def unparse(value: ASTValue): String = value match {
    case ASTValue.Boolean(value, _) => value.toString
    case ASTValue.Int(value, _) => value.toString
    case ASTValue.Float(value, _) => value.toString
    case ASTValue.String(value, _) =>
      "\"" + value.replace("\n", "\\n").replace("\"", "\\\"") + "\""

    case ASTValue.Struct(props, _) =>
      s"Struct(${props.map { case (k, v) => s"$k = ${unparse(v)}" }.mkString(", ")})"

    case ASTValue.Lambda(params, body, _) =>
      s"(${params.map(unparse).mkString(", ")}) { ${unparse(body)} }"

    case ASTValue.Block(values, _) =>
      values.map(unparse).mkString("; ")

    case ASTValue.Call(t, name, arguments, _, _) =>
      val target = unparse(t)

      s"${if (target == "self") "" else s"$target."}$name(${unparse(arguments)})"

    case ASTValue.NameReference(name, _) => name

    case ASTValue.Let(name, value, block, _) => s"$name = ${unparse(value)}; ${unparse(block)}"

    case _ => throw new Exception(s"Cannot unparse value ${value.inspectAST}")
  }

  def unparse(parameter: ASTParameter): String = {
    parameter match {
      case ASTParameter(name, Some(typeValue)) => s"$name: ${unparse(typeValue)}"
      case ASTParameter(name, None) => name
    }
  }

  def unparse(arguments: ASTArguments): String = {
    val positionalArguments = arguments.positional.map(unparse)
    val namedArguments = arguments.named.map { case (name, value) => s"${name} = ${unparse(value)}" }

    (positionalArguments ++ namedArguments).mkString(", ")
  }
}
