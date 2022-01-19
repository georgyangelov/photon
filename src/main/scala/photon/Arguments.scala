package photon

import photon.core.operations.EParameter
import photon.interpreter.EvalError

case class Arguments[+T](
  self: T,
  positional: Seq[T],
  named: Map[String, T]
) {
  def changeSelf[R >: T](value: R) = Arguments[R](value, positional, named)

  def map[R](f: T => R) = Arguments(
    f(self),
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def forall(f: T => Boolean) = f(self) && positional.forall(f) && named.view.values.forall(f)

  def get(index: Int, name: String): T = {
    if (index == 0) {
      self
    } else if (index - 1 < positional.size) {
      positional(index - 1)
    } else {
      named.get(name) match {
        case Some(value) => value
        case None => throw EvalError(s"Missing argument $name (at index $index)", None)
      }
    }
  }

  def toPositional(params: Seq[EParameter]) = {
    // TODO: Optional parameters?
    val namedParamsInOrder = params.drop(positional.length)
      .map { param => named.get(param.name) }

    Arguments.positional(
      self,
      positional ++ namedParamsInOrder
    )
  }

  //  override def toString = Unparser.unparse(
  //    ValueToAST.transformForInspection(
  //      this.asInstanceOf[Arguments[Value]]
  //    )
  //  )
}

object Arguments {
  def empty[T](self: T): Arguments[T] = Arguments(self, Seq.empty, Map.empty)

  def positional[T](self: T, values: Seq[T]) = Arguments[T](
    self = self,
    positional = values,
    named = Map.empty
  )

  def named[T](self: T, values: Map[String, T]) = Arguments[T](
    self = self,
    positional = Seq.empty,
    named = values
  )
}