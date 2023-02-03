package photon.compiler.macros

import photon.core.Location
import photon.frontend.*

object StaticMacro {
  fun staticMacro(parser: Parser, location: Location): ASTValue {
    val functionAST = parser.parseAST(ASTValue.Function::class.java)
    val function = builderFunction(functionAST)

    return ASTValue.Call(
      target = ASTValue.NameReference("self", location),
      "static",
      ASTArguments(
        listOf(function),
        emptyMap()
      ),
      mayBeVarCall = false,
      location.extendWith(parser.lastLocation)
    )
  }

  private fun builderFunction(function: ASTValue.Function): ASTValue {
    val classBuilderReference = ASTValue.NameReference("ClassBuilder", function.location)

    return ASTValue.Function(
      params = listOf(
        ASTParameter(
          "self",
          "self",
          Pattern.SpecificValue(classBuilderReference),
          function.location
        ),
      ) + function.params,
      body = function.body,
      returnType = function.returnType,
      isCompileTimeOnly = true,
      location = function.location
    )
  }
}