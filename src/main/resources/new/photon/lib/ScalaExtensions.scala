package photon.lib

object ScalaExtensions {
  implicit class IterableSetExtensions[T](seqOfSets: Iterable[Set[T]]) {
    def unionSets: Set[T] = seqOfSets.fold(Set.empty) { case (a, b) => a ++ b }
  }
}