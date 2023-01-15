package photon.frontend

import photon.core.Location

data class ASTParameter(
  val outName: String,
  val inName: String,
  val typePattern: Pattern?,
  val location: Location?
)

data class ASTTypeParameter(
  val name: String,
  val typ: ASTValue,
  val location: Location?
)

data class ASTPatternParameter(
  val name: String,
  val typ: Pattern,
  val location: Location?
)

data class ArgumentsWithoutSelf<T>(
  val positional: List<T>,
  val named: Map<String, T>
) {
  companion object {
    fun <T>empty(): ArgumentsWithoutSelf<T> = ArgumentsWithoutSelf(emptyList(), emptyMap())
  }
}

data class ASTArguments(
  val positional: List<ASTValue>,
  val named: Map<String, ASTValue>
) {
  companion object {
    val empty = ASTArguments(emptyList(), emptyMap())
  }
}

sealed class ASTValue {
  abstract val location: Location?
  abstract fun inspect(): kotlin.String

  data class Boolean(val value: kotlin.Boolean, override val location: Location?) : ASTValue() {
    override fun inspect(): kotlin.String = value.toString()
  }

  data class Int(val value: kotlin.Int, override val location: Location?) : ASTValue() {
    override fun inspect(): kotlin.String = value.toString()
  }

  data class Float(val value: Double, override val location: Location?) : ASTValue() {
    override fun inspect(): kotlin.String = value.toString()
  }

  data class String(val value: kotlin.String, override val location: Location?) : ASTValue() {
    override fun inspect(): kotlin.String {
      val escapedString = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

      return '"' + escapedString + '"'
    }
  }

  data class Block(val values: List<ASTValue>, override val location: Location?) : ASTValue() {
    override fun inspect(): kotlin.String {
      return if (values.isNotEmpty()) {
        "{ ${values.joinToString(" ") { it.inspect() }} }"
      } else {
        "{}"
      }
    }
  }

  data class Function(
    val params: List<ASTParameter>,
    val body: ASTValue,
    val returnType: ASTValue?,
    val isCompileTimeOnly: kotlin.Boolean,
    override val location: Location?
  ) : ASTValue() {
    override fun inspect(): kotlin.String {
      val bodyAST = body.inspect()
      val paramsAST = params.joinToString(" ") {
        if (it.typePattern != null) {
          if (it.outName == it.inName) {
            "(param ${it.outName} ${it.typePattern.inspect()})"
          } else {
            "(param ${it.outName} ${it.inName} ${it.typePattern.inspect()})"
          }
        } else {
          if (it.outName == it.inName) {
            "(param ${it.outName})"
          } else {
            "(param ${it.outName} ${it.inName})"
          }
        }
      }

      val returnTypeAST =
        if (returnType != null) "${returnType.inspect()} "
        else ""

      val functionType =
        if (isCompileTimeOnly) "@lambda"
        else "lambda"

      return "($functionType [$paramsAST] $returnTypeAST$bodyAST)"
    }
  }

  data class Call(
    val target: ASTValue,
    val name: kotlin.String,
    val arguments: ASTArguments,
    val mayBeVarCall: kotlin.Boolean,
    override val location: Location?
  ) : ASTValue() {
    override fun inspect(): kotlin.String {
      val positionalArguments = arguments.positional.map { it.inspect() }
      val namedArguments = arguments.named.map { "(param ${it.key} ${it.value.inspect()})" }
      val argumentStrings = positionalArguments + namedArguments

      return if (argumentStrings.isEmpty()) {
        "($name ${target.inspect()})"
      } else {
        "($name ${target.inspect()} ${argumentStrings.joinToString(" ")})"
      }
    }
  }

  data class NameReference(val name: kotlin.String, override val location: Location?): ASTValue() {
    override fun inspect(): kotlin.String = name
  }

  data class Let(
    val name: kotlin.String,
    val value: ASTValue,
    val isRecursive: kotlin.Boolean,
    override val location: Location?
  ): ASTValue() {
    override fun inspect(): kotlin.String =
      "(${if (isRecursive) "recursive-let" else "let"} $name ${value.inspect()})"
  }

  data class FunctionType(
    val params: List<ASTTypeParameter>,
    val returnType: ASTValue,
    override val location: Location?
  ) : ASTValue() {
    override fun inspect(): kotlin.String {
      val paramsAST = params
        .joinToString(" ") { "(param ${it.name} ${it.typ.inspect()})" }

      return "(Function [$paramsAST] ${returnType.inspect()})"
    }
  }

  data class TypeAssert(
    val value: ASTValue,
    val type: ASTValue,
    override val location: Location?
  ) : ASTValue() {
    override fun inspect(): kotlin.String {
      return "(typeAssert ${value.inspect()} ${type.inspect()})"
    }
  }
}

sealed class Pattern {
  abstract val location: Location?
  abstract fun inspect(): String

  data class SpecificValue(val value: ASTValue) : Pattern() {
    override val location: Location?
      get() = value.location

    override fun inspect(): String = value.inspect()
  }

  data class Binding(val name: String, override val location: Location?): Pattern() {
    override fun inspect(): String = "(val $name)"
  }

  data class Call(
    val target: ASTValue,
    val name: String,
    val arguments: ArgumentsWithoutSelf<Pattern>,
    val mayBeVarCall: Boolean,
    override val location: Location?
  ) : Pattern() {
    override fun inspect(): String {
      val positionalArguments = arguments.positional.map { it.inspect() }
      val namedArguments = arguments.named.map { "(param ${it.key} ${it.value.inspect()})" }
      val argumentStrings = positionalArguments + namedArguments

      return if (argumentStrings.isEmpty()) {
        "<$name ${target.inspect()}>"
      } else {
        "<$name ${target.inspect()} ${argumentStrings.joinToString(" ")}>"
      }
    }
  }

  data class FunctionType(
    val params: List<ASTPatternParameter>,
    val returnType: Pattern,
    override val location: Location?
  ) : Pattern() {
    override fun inspect(): String {
      val paramsAST = params
        .map { "(param ${it.name} ${it.typ.inspect()})" }
        .joinToString(" ")

      return "(Function [$paramsAST] ${returnType.inspect()})"
    }
  }
}

sealed class ASTValueOrPattern {
  abstract val location: Location?

  data class Value(val value: ASTValue) : ASTValueOrPattern() {
    override val location: Location?
      get() = value.location
  }

  data class Pattern(val pattern: photon.frontend.Pattern) : ASTValueOrPattern() {
    override val location: Location?
      get() = pattern.location
  }
}