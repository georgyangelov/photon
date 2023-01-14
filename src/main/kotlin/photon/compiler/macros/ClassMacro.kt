package photon.compiler.macros

import photon.core.Location
import photon.frontend.*

object ClassMacro {
  fun classMacro(parser: Parser, location: Location): ASTValue {
    return readDefinition("Class", parser, location)
  }

  fun objectMacro(parser: Parser, location: Location): ASTValue {
    TODO()
  }

  fun interfaceMacro(parser: Parser, location: Location): ASTValue {
    return readDefinition("Interface", parser, location)
  }

  private fun readDefinition(type: String, parser: Parser, location: Location): ASTValue {
    val hasName = parser.token.tokenType == TokenType.Name
    val name =
      if (hasName)
        parser.readToken(TokenType.Name, "Expected a name after $type")
      else null

    val builderFunction = parser.parseAST(ASTValue.Function::class.java)

    val classDefinitionLocation = location.extendWith(parser.lastLocation)

    val builderFunctionWithSelfArgument = ASTValue.Function(
      params = listOf(
        ASTParameter(
          "self",
          "self",
          Pattern.SpecificValue(ASTValue.NameReference("ClassBuilder", builderFunction.location)),
          location
        )
      ),

      body = builderFunction.body,

      // TODO: Nothing
      returnType = ASTValue.NameReference("Int", builderFunction.location),

      isCompileTimeOnly = true,

      location = classDefinitionLocation
    )

    if (name != null) {
      val block = parser.parseRestOfBlock()
      val letLocation = location.extendWith(parser.lastLocation)

      return ASTValue.Let(
        name = name.string,
        value = ASTValue.Call(
          target = ASTValue.NameReference(type, location),
          name = "new",
          arguments = ASTArguments(
            listOf(
              ASTValue.String(name.string, name.location),
              builderFunctionWithSelfArgument
            ),
            emptyMap()
          ),
          mayBeVarCall = false,
          classDefinitionLocation
        ),
        block = block,
        location = letLocation
      )
    } else {
      return ASTValue.Call(
        target = ASTValue.NameReference(type, location),
        name = "new",
        arguments = ASTArguments(
          listOf(builderFunctionWithSelfArgument),
          emptyMap()
        ),
        mayBeVarCall = false,
        classDefinitionLocation
      )
    }
  }
}