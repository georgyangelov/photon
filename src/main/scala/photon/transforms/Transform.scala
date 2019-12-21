package photon.transforms

import photon.{Lambda, Operation, Struct, Value}

abstract class Transform {
  type Next = Value => Value;

  def transform(value: Value): Value

  def next(value: Value): Value = {
    value match {
      case Value.Unknown(_)    |
           Value.Nothing(_)    |
           Value.Boolean(_, _) |
           Value.Int(_, _)     |
           Value.Float(_, _)   |
           Value.Operation(Operation.NameReference(_), _) |
           Value.String(_, _) => value

      case Value.Struct(Struct(props), location) =>
        val newProps = props.map { case (key, value) => (key, transform(value)) }

        Value.Struct(Struct(newProps), location)

      case Value.Lambda(Lambda(params, scope, body), location) =>
        val newBody = Operation.Block(body.values.map(transform))

        Value.Lambda(Lambda(params, scope, newBody), location)

      case Value.Operation(Operation.Block(values), location) =>
        Value.Operation(Operation.Block(values.map(transform)), location)

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val newTarget = transform(target)
        val newArguments = arguments.map(transform)

        Value.Operation(Operation.Call(
          target = newTarget,
          name = name,
          arguments = newArguments,
          mayBeVarCall = mayBeVarCall
        ), location)

      case Value.Operation(Operation.Assignment(name, value), location) =>
        val newValue = transform(value)

        Value.Operation(Operation.Assignment(name, newValue), location)
    }
  }
}
