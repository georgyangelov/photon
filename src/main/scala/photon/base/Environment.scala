package photon.base

import scala.reflect.ClassTag

case class Environment(
  scope: Scope,
  evalMode: EvalMode
) {
  def require[T <: Value](name: VarName)(implicit tag: ClassTag[T]) =
    scope
      .find(name)
      .getOrElse { throw EvalError(s"Cannot find name $name in $scope", None) }
      .evaluate(this) match {
      case value: T => value
      case value => throw EvalError(s"Expected $name ($value) to be of type $tag", None)
    }
}
