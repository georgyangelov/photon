package photon.compiler.core

sealed class Interface: Type() {
  abstract fun assignableFrom(other: Type): PossibleTypeError<ValueWrapper>
}
