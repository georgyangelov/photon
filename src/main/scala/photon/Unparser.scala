package photon

object Unparser {
  def unparse(value: Value): String = value match {
    case Value.Unknown(_) => "$?"
    case Value.Nothing(_) => "$nothing"
    case Value.Boolean(value, _) => value.toString
    case Value.Int(value, _, _) => value.toString
    case Value.Float(value, _) => value.toString
    case Value.String(value, _) =>
      "\"" + value.replace("\n", "\\n").replace("\"", "\\\"") + "\""

    case Value.Native(native, _) => s"<${native.toString}>"
    case Value.Struct(struct, _) => unparse(struct)
    case Value.Lambda(lambda, _) => unparse(lambda)
    case Value.Operation(operation, _) => unparse(operation)

    case _ => throw new Exception(s"Cannot unparse value ${value.inspectAST}")
  }

  def unparse(operation: Operation): String = operation match {
    case Operation.Block(values) =>
      values.map(unparse).mkString("; ")

    case Operation.Call(t, name, arguments, _) =>
      val target = unparse(t)

      s"${if (target == "self") "" else s"$target."}$name(${unparse(arguments)})"

    case Operation.NameReference(name) => name

    case Operation.Let(name, value, block) => s"$name = ${unparse(value)}; ${unparse(block)}"
  }

  def unparse(struct: Struct): String =
    s"Struct(${struct.props.map { case (k, v) => s"$k = ${unparse(v)}" }.mkString(", ")})"

  def unparse(lambda: Lambda): String = {
    val isCompileTimeOnly = !lambda.traits.contains(LambdaTrait.Runtime)

    s"(${lambda.params.map(unparse).mkString(", ")}) { ${unparse(lambda.body)} }${if (isCompileTimeOnly) ".compileTimeOnly" else ""}"
  }

  def unparse(parameter: Parameter): String = {
    parameter match {
      case Parameter(name, Some(typeValue)) => s"$name: ${unparse(typeValue)}"
      case Parameter(name, None) => name
    }
  }

  def unparse(arguments: Arguments): String = {
    val positionalArguments = arguments.positional.map(unparse)
    val namedArguments = arguments.named.map { case (name, value) => s"${name} = ${unparse(value)}" }

    (positionalArguments ++ namedArguments).mkString(", ")
  }
}
