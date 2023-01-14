package photon.compiler.core

abstract class Interface: Type() {
  abstract fun assignableFrom(other: Type): PossibleTypeError<NodeWrapper>
}
