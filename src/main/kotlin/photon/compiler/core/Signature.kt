package photon.compiler.core

import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.PhotonLanguage
import photon.compiler.nodes.PatternNode
import photon.core.Location
import photon.core.TypeError
import photon.frontend.ArgumentsWithoutSelf

sealed class PossibleTypeError<T> {
  abstract fun <R>map(fn: (T) -> R): PossibleTypeError<R>
  abstract fun mapError(fn: (TypeError) -> TypeError): PossibleTypeError<T>
  abstract fun wrapError(message: String, location: Location?): PossibleTypeError<T>
  abstract fun getOrThrowError(): T

  class Error<T>(val error: TypeError): PossibleTypeError<T>() {
    override fun <R> map(fn: (T) -> R) = Error<R>(error)
    override fun mapError(fn: (TypeError) -> TypeError) = Error<T>(fn(error))
    override fun wrapError(message: String, location: Location?) = Error<T>(error.wrap(message, location))
    override fun getOrThrowError(): Nothing = throw error
  }

  class Success<T>(val value: T): PossibleTypeError<T>() {
    override fun <R> map(fn: (T) -> R) = Success(fn(value))
    override fun mapError(fn: (TypeError) -> TypeError) = this
    override fun wrapError(message: String, location: Location?) = this
    override fun getOrThrowError(): T = value
  }
}

sealed class Signature {
  abstract fun hasSelfArgument(): Boolean
  abstract fun withoutSelfArgument(): Signature

  // TODO: Remove named arguments here
  abstract fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Pair<Concrete, List<ValueConverter>>>
  abstract fun assignableFrom(other: Signature): PossibleTypeError<CallConversion>

  class Any(val returnType: Type): Signature() {
    override fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Pair<Concrete, List<ValueConverter>>> {
      val argsWithNames = types.positional.withIndex().map { Pair("_${it.index}", it.value) }
      val signature = Concrete(argsWithNames, returnType)
      val conversions = argsWithNames.map { CallConversion.identity }

      return PossibleTypeError.Success(
        Pair(signature, conversions)
      )
    }

    // TODO: This is not correct, how do we handle it?
    override fun hasSelfArgument(): Boolean = true
    override fun withoutSelfArgument(): Signature = this

    override fun assignableFrom(other: Signature): PossibleTypeError<CallConversion> = when (other) {
      is Any ->
        Core.isTypeAssignable(other.returnType, returnType)
          .map { CallConversion(emptyList(), it) }

      is Concrete ->
        Core.isTypeAssignable(other.returnType, returnType)
          .map { CallConversion(emptyList(), it) }
    }
  }

  class Concrete(val argTypes: List<Pair<String, Type>>, val returnType: Type): Signature() {
    override fun instantiate(types: ArgumentsWithoutSelf<Type>): PossibleTypeError<Pair<Concrete, List<ValueConverter>>> {
      if (argTypes.size != types.positional.size) {
        return PossibleTypeError.Error(TypeError(
          "Different number of arguments: expected ${argTypes.size}, got ${types.positional.size}",
          // TODO: Location
          null
        ))
      }

      val conversions = argTypes.zip(types.positional).map { (toTypeEntry, fromType) ->
        val (name, toType) = toTypeEntry

        val conversion = when (val result = Core.isTypeAssignable(fromType, toType)) {
          is PossibleTypeError.Error -> return PossibleTypeError.Error(
            TypeError("Incompatible types for parameter $name: ${result.error.message}", result.error.location)
          )

          is PossibleTypeError.Success -> result.value
        }

        conversion
      }

      return PossibleTypeError.Success(Pair(this, conversions))
    }

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

    override fun assignableFrom(other: Signature): PossibleTypeError<CallConversion> {
      return when (other) {
        is Any -> Core.isTypeAssignable(other.returnType, returnType).map {
          CallConversion(argTypes.map { CallConversion.identity }, it)
        }

        is Concrete -> {
          if (argTypes.size != other.argTypes.size) {
            // TODO: Location
            return PossibleTypeError.Error(TypeError("Different argument counts", null))
          }

          val argumentConversions = mutableListOf<ValueConverter>()

          for (i in argTypes.indices) {
            val (_, aType) = argTypes[i]
            val (_, bType) = other.argTypes[i]

            when (val result = Core.isTypeAssignable(aType, bType)) {
              is PossibleTypeError.Error -> return PossibleTypeError.Error(result.error)
              is PossibleTypeError.Success -> argumentConversions.add(result.value)
            }
          }

          return Core.isTypeAssignable(other.returnType, returnType)
            .map { CallConversion(argumentConversions, it) }
        }
      }
    }
  }
}

typealias ValueConverter = (value: Any) -> Any

class CallConversion(
  val argumentConversions: List<ValueConverter>,
  val returnConversion: ValueConverter
) {
  companion object {
    val identity: ValueConverter = { it }
  }
}