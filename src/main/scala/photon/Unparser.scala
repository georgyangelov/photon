package photon

object Unparser {
  def unparse(value: Value): String = value match {
    case Value.Unknown(_) => "$?"
    case Value.Nothing(_) => "$nothing"
    case Value.Boolean(value, _) => value.toString
    case Value.Int(value, _) => value.toString
    case Value.Float(value, _) => value.toString
    case Value.String(value, _) =>
      "\"" + value.toString.replace("\n", "\\n").replace("\"", "\\\"") + "\""

    case Value.Struct(struct, _) => unparse(struct)
    case Value.Lambda(lambda, _) => unparse(lambda)
    case Value.Operation(operation, _) => unparse(operation)
  }

  def unparse(operation: Operation): String = operation match {
    case Operation.Assignment(name, value) =>
      s"$name = ${unparse(value)}"

    case Operation.Block(values) =>
      s"{ ${values.map(unparse).mkString("; ")} }"

    case Operation.Call(t, name, arguments, mayBeVarCall) =>
      val target = unparse(t)
      s"${if (target == "self") "" else s"${target}."}${name}(${arguments.map(unparse).mkString(", ")})"

    case Operation.NameReference(name) => name
  }

  def unparse(struct: Struct): String =
    s"$${ ${struct.props.map { case (k, v) => s"$k: ${unparse(v)}" }.mkString(", ")} }"

  def unparse(lambda: Lambda): String =
    s"{ |${lambda.params.mkString(", ")}| ${lambda.body.values.map(unparse).mkString("; ")} }"
}