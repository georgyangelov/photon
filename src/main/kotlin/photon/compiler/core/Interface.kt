package photon.compiler.core

abstract class Interface: Type() {
  abstract fun conversionFrom(other: Type): PossibleTypeError<ValueConverter>
}
