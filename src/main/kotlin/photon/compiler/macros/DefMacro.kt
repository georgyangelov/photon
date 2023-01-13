package photon.compiler.macros

import photon.core.Location
import photon.frontend.*

object DefMacro {
  fun defMacro(parser: Parser, location: Location): ASTValue {
    val name = parser.readToken(TokenType.Name, "Expected a name after `def`")

    val typeOrFunction =
      if (parser.token.tokenType == TokenType.Colon) {
        // def nextAge: Int { age + 1 }
        // def nextAge: Int
        parser.readToken(TokenType.Colon, "Expected a : Type")

        val returnType = parser.parseNext(requireCallParens = true)

        if (parser.token.tokenType == TokenType.OpenBrace) {
          // def nextAge: Int { age + 1 }
          val function = parser.parseAST(ASTValue.Function::class.java)

          addSelfAndTypeToFunction(function, returnType)
        } else {
          // def nextAge: Int
          returnType
        }
      } else {
        when (val value = parser.parseNext()) {
          // def agePlus(x: Int) { age + 1 }
          is ASTValue.Function -> addSelfAndTypeToFunction(value, null)

          // def agePlus(x: Int): Int
          else -> value
        }
      }

    val definitionLocation = location.extendWith(parser.lastLocation)

    return ASTValue.Call(
      target = ASTValue.NameReference("self", location),
      "define",
      ASTArguments(
        listOf(
          ASTValue.String(name.string, name.location),
          typeOrFunction
        ),
        emptyMap()
      ),
      mayBeVarCall = false,
      definitionLocation
    )
  }

  private fun addSelfAndTypeToFunction(function: ASTValue.Function, returnType: ASTValue?): ASTValue {
    val selfTypeCall = ASTValue.Call(
      target = ASTValue.NameReference("self", function.location),
      "selfType",
      ASTArguments(emptyList(), emptyMap()),
      mayBeVarCall = false,
      function.location
    )

    return ASTValue.Function(
      params = listOf(
        ASTParameter("self", "self", Pattern.SpecificValue(selfTypeCall), function.location),
      ) + function.params,
      body = function.body,
      returnType = returnType ?: function.returnType,
      location = function.location
    )
  }
}