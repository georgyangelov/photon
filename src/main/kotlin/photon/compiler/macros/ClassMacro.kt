package photon.compiler.macros

import photon.core.Location
import photon.frontend.*

object ClassMacro {
  fun classMacro(parser: Parser, location: Location): ASTValue {
    return readDefinition("Class", parser, location)
  }

  fun objectMacro(parser: Parser, location: Location): ASTValue {
    val hasName = parser.token.tokenType == TokenType.Name
    val name =
      if (hasName)
        parser.readToken(TokenType.Name, "Expected a name after object")
      else null

    val classDefinition = readDefinition("Class", parser, location, name?.string)

    val newObject = ASTValue.Call(
      target = classDefinition,
      name = "new",
      arguments = ASTArguments(emptyList(), emptyMap()),
      mayBeVarCall = false,
      location = classDefinition.location
    )

    if (name != null) {
      return ASTValue.Let(
        name = name.string,
        value = newObject,
        isRecursive = true,
        location = classDefinition.location
      )
    }

    return newObject
  }

  fun interfaceMacro(parser: Parser, location: Location): ASTValue {
    return readDefinition("Interface", parser, location)
  }

  private fun readDefinition(
    type: String,
    parser: Parser,
    location: Location,
    className: String? = null
  ): ASTValue {
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
        isRecursive = true,
        location = letLocation
      )
    } else {
      return ASTValue.Call(
        target = ASTValue.NameReference(type, location),
        name = "new",
        arguments = ASTArguments(
          if (className != null) {
            listOf(
              ASTValue.String(className, location),
              builderFunctionWithSelfArgument
            )
          } else {
            listOf(builderFunctionWithSelfArgument)
          },
          emptyMap()
        ),
        mayBeVarCall = false,
        classDefinitionLocation
      )
    }
  }
}