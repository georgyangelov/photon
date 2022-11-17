package photon.lib

object ScalaExtensions {
  implicit class IterableSetExtensions[T](seqOfSets: Iterable[Set[T]]) {
    def unionSets: Set[T] = seqOfSets.fold(Set.empty) { case (a, b) => a ++ b }
  }

  implicit class IterableExtensions[T](iterable: Iterable[T]) {
    def mapWithRollingContext[C, R](initialContext: C)(fn: (C, T) => (C, R)): (C, Iterable[R]) = {
      var context = initialContext
      val results = iterable.map { element =>
        val (newContext, result) = fn(context, element)

        context = newContext

        result
      }

      (context, results)
    }
  }
}