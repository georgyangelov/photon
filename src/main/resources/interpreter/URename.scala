package photon.interpreter

import photon.{UFunction, ULiteral, UOperation, UParameter, UValue, VariableName}

object URename {
  // TODO: Optimize to check if value needs renaming via unboundNames
  def rename(value: UValue, renames: Map[VariableName, VariableName]): UValue = {
    value match {
      case ULiteral.Nothing(_) => value
      case ULiteral.Boolean(_, _) => value
      case ULiteral.Int(_, _) => value
      case ULiteral.Float(_, _) => value
      case ULiteral.String(_, _) => value

      case UOperation.Block(values, location) =>
        UOperation.Block(
          values.map(rename(_, renames)),
          location
        )

      case UOperation.Let(name, value, block, location) =>
        UOperation.Let(
          renames.getOrElse(name, name),
          rename(value, renames),
          rename(block, renames),
          location
        )

      case UOperation.Reference(name, location) =>
        UOperation.Reference(
          renames.getOrElse(name, name),
          location
        )

      case UOperation.Function(fn, location) =>
        UOperation.Function(
          new UFunction(
            fn.params.map(param => UParameter(param.name, rename(param.typ, renames), param.location)),
            fn.nameMap.map { case string -> name => string -> renames.getOrElse(name, name) },
            rename(fn.body, renames),
            fn.returnType.map(rename(_, renames))
          ),
          location
        )

      case UOperation.Call(name, arguments, location) =>
        UOperation.Call(
          name,
          arguments.map(rename(_, renames)),
          location
        )
    }
  }
}
