package photon.transforms

import photon.{Lambda, Operation, Value, Scope}

object LambdaScopeTransform extends Transform[Scope] {
  override def transform(value: Value, scope: Scope): Value = value match {
    case Value.Lambda(Lambda(params, _, body), location) =>
      val innerScope = Scope(
        // TODO
      )

      next(Value.Lambda(Lambda(params, Some(scope), body), location), )

    case value @ _ => value
  }
}
