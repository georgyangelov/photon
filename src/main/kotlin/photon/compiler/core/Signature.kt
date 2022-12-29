package photon.compiler.core

import photon.core.TypeError
import photon.frontend.ArgumentsWithoutSelf

sealed class PossibleTypeError<T> {
  abstract fun <R>map(fn: (T) -> R): PossibleTypeError<R>

  class Error<T>(val error: TypeError): PossibleTypeError<T>() {
    override fun <R> map(fn: (T) -> R): PossibleTypeError<R> = Error(error)
  }

  class Success<T>(val value: T): PossibleTypeError<T>() {
    override fun <R> map(fn: (T) -> R): PossibleTypeError<R> = Success(fn(value))
  }
}

sealed class Signature {
  abstract val returnType: Type

  abstract fun hasSelfArgument(): Boolean
  abstract fun withoutSelfArgument(): Signature

  abstract fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Concrete>
  abstract fun assignableFrom(other: Signature): PossibleTypeError<Unit>

  class Any(override val returnType: Type): Signature() {
    override fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Concrete> {
      val argsWithNames = types.positional.withIndex().map { Pair("_${it.index}", it.value) }

      return PossibleTypeError.Success(Concrete(argsWithNames, returnType))
    }

    // TODO: This is not correct, how do we handle it?
    override fun hasSelfArgument(): Boolean = true
    override fun withoutSelfArgument(): Signature = this

    override fun assignableFrom(other: Signature): PossibleTypeError<Unit> = when (other) {
      is Any -> Core.isTypeAssignable(other.returnType, returnType).map { }
      is Concrete -> Core.isTypeAssignable(other.returnType, returnType).map { }
    }
  }

  class Concrete(val argTypes: List<Pair<String, Type>>, override val returnType: Type): Signature() {
    override fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Concrete> = PossibleTypeError.Success(this)

    override fun hasSelfArgument(): Boolean {
      return argTypes.any { it.first == "self" }
    }

    override fun withoutSelfArgument(): Signature {
      val argsWithoutName = mutableListOf<Pair<String, Type>>()
      var foundArgument = false

      // We only want to drop the first argument named this way.
      // This is because we want to be able to define class methods that
      // have a different `self` argument
      for (type in argTypes) {
        if (type.first == "self" && !foundArgument) {
          foundArgument = true
        } else {
          argsWithoutName.add(type)
        }
      }

      return Concrete(argsWithoutName, returnType)
    }

    override fun assignableFrom(other: Signature): PossibleTypeError<Unit> {
      return when (other) {
        is Any -> Core.isTypeAssignable(other.returnType, returnType).map { }

        is Concrete -> {
          if (argTypes.size != other.argTypes.size) {
            // TODO: Location
            return PossibleTypeError.Error(TypeError("Different argument counts", null))
          }

          for (i in argTypes.indices) {
            val (aName, aType) = argTypes[i]
            val (bName, bType) = other.argTypes[i]

//            TODO: Do we care?
//            if (aName != bName) {
//              // TODO: Location
//              return PossibleTypeError.Error(TypeError("Different argument names", null))
//            }

            val result = Core.isTypeAssignable(bType, aType)
            if (result is PossibleTypeError.Error<*>) {
              return result.map { }
            }
          }

          Core.isTypeAssignable(other.returnType, returnType).map { }
        }
      }
    }
  }
}