package photon.transforms

import photon.{Arguments, Lambda, Operation, Struct, Value}

abstract class Transform[Context] {
  type Next = Value => Value;

  def transform(value: Value, context: Context): Value

  def next(value: Value, context: Context): Value = {
    value match {
      case Value.Unknown(_)    |
           Value.Nothing(_)    |
           Value.Boolean(_, _) |
           Value.Int(_, _)     |
           Value.Float(_, _)   |
           Value.Operation(Operation.NameReference(_), _) |
           Value.String(_, _) |
           Value.Native(_, _) => value

      case Value.Struct(Struct(props), location) =>
        val newProps = props.map { case (key, value) => (key, transform(value, context)) }

        Value.Struct(Struct(newProps), location)

      case Value.Lambda(Lambda(params, scope, body), location) =>
        val newBody = Operation.Block(body.values.map(transform(_, context)))

        Value.Lambda(Lambda(params, scope, newBody), location)

      case Value.Operation(Operation.Block(values), location) =>
        Value.Operation(Operation.Block(values.map(transform(_, context))), location)

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), location) =>
        val newTarget = transform(target, context)
        val newArguments = Arguments(
          arguments.positional.map(transform(_, context)),
          arguments.named.view.mapValues(transform(_, context)).toMap
        )

        Value.Operation(Operation.Call(
          target = newTarget,
          name = name,
          arguments = newArguments,
          mayBeVarCall = mayBeVarCall
        ), location)

      case Value.Operation(Operation.Assignment(name, value), location) =>
        val newValue = transform(value, context)

        Value.Operation(Operation.Assignment(name, newValue), location)
    }
  }
}
