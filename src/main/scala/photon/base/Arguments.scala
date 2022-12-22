package photon.base

case class ArgumentsWithoutSelf[+T](positional: Seq[T], named: Map[String, T]) {
  def argValues = positional ++ named.values

  def map[R](f: T => R) = ArgumentsWithoutSelf(
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def matchWith[T1](params: Seq[(String, T1)]): Seq[(String, (T, T1))] = {
    val (namedParams, positionalParams) = params.partition { case (name, _) => named.contains(name) }

    val positionalWithT1 = positionalParams
      .zip(positional)
      .map { case ((name, t1), value) => (name, (value, t1)) }

    val namedWithT1 = namedParams
      .map { case (name, t1) => (name, (named(name), t1)) }

    positionalWithT1 ++ namedWithT1
  }
}
object ArgumentsWithoutSelf {
  def empty[T]: ArgumentsWithoutSelf[T] = ArgumentsWithoutSelf(Seq.empty, Map.empty)

  def positional[T](values: Seq[T]) =
    ArgumentsWithoutSelf[T](
      positional = values,
      named = Map.empty
    )

  def named[T](values: Map[String, T]) =
    ArgumentsWithoutSelf[T](
      positional = Seq.empty,
      named = values
    )
}

case class Arguments[+T](
  self: T,
  positional: Seq[T],
  named: Map[String, T]
) {
  def values = positional ++ named.values ++ Seq(self)
  def argValues = positional ++ named.values

  def count = positional.size + named.size

  def withoutSelf = ArgumentsWithoutSelf[T](positional, named)
  def changeSelf[R >: T](value: R) = Arguments[R](value, positional, named)

  def map[R](f: T => R) = Arguments(
    f(self),
    positional.map(f),
    named.view.mapValues(f).toMap
  )

  def mapWithNames[R >: T](names: Seq[String])(f: (String, T) => R) = {
    val positionalNames = names.filterNot(named.contains)

    Arguments(
      self,
      positionalNames.zip(positional).map { case (name, value) => f(name, value) },
      named.map { case (name, value) => name -> f(name, value) }
    )
  }

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

  def matchWithNamesUnordered(paramNames: Seq[String]): Seq[(String, T)] = {
    val positionalNames = paramNames.filterNot(named.contains)

    val positionalParams = positionalNames.zip(positional)
    val namedParams = named.toSeq

    positionalParams ++ namedParams
  }
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