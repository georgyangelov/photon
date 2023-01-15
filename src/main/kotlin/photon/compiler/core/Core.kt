package photon.compiler.core

import photon.core.TypeError

class Core {
  companion object {
    fun isTypeAssignable(from: Type, to: Type): PossibleTypeError<ValueConverter> {
      if (to == AnyStatic) return PossibleTypeError.Success { it }
      else if (to == from) return PossibleTypeError.Success { it }
      else if (to is Interface) return to.conversionFrom(from)
      // TODO: Location
      else return PossibleTypeError.Error(TypeError("Cannot assign type $from to $to", null))
    }
  }
}