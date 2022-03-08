package photon.frontend.macros

import photon.Location
import photon.frontend.{ASTArguments, ASTParameter, ASTValue, Parser, TokenType}

object ClassMacros {
  def classMacro(parser: Parser, location: Location): ASTValue = {
    val name = parser.readToken(TokenType.Name, "Expected a name after class")
    val builderFn = parser.parseAST[ASTValue.Function]

    val builderFnWithSelfArg =
        ASTValue.Function(
          params = Seq(
            ASTParameter("self",
              Some(ASTValue.NameReference("ClassBuilder", Some(location))),
              Some(location)
            )
          ),
          body = builderFn.body,

          // TODO: NothingType
          returnType = None,

          location = builderFn.location
        )

    val classDefLocation = location.extendWith(parser.lastLocation)
    val block = parser.parseRestOfBlock()
    val letLocation = location.extendWith(parser.lastLocation)

    ASTValue.Let(
      name.string,
      ASTValue.Call(
        ASTValue.NameReference("Class", Some(location)),
        "new",
        ASTArguments.positional(Seq(
          ASTValue.String(name.string, Some(name.location)),
          builderFnWithSelfArg
        )),
        mayBeVarCall = false,
        Some(classDefLocation)
      ),
      block,
      Some(letLocation)
    )
  }

  def defMacro(parser: Parser, location: Location): ASTValue = {
    val name = parser.readToken(TokenType.Name, "Expected a name after def")

    val value = if (parser.token.tokenType == TokenType.Colon) {
      // def nextAge: Int { age + 1 }
      // def nextAge: Int
      parser.readToken(TokenType.Colon, "Expected a : Type")
      val returnType = parser.parseNext(requireCallParens = true)

      if (parser.token.tokenType == TokenType.OpenBrace) {
        // def nextAge: Int { age + 1 }
        val fn = parser.parseAST[ASTValue.Function]

        addSelfAndTypeToFn(fn, Some(returnType))
      } else {
        // def nextAge: Int
        returnType
      }
    } else {
      // def agePlus(x: Int) { age + 1 }
      val fn = parser.parseAST[ASTValue.Function]

      addSelfAndTypeToFn(fn, None)
    }

    val wholeDefLocation = location.extendWith(parser.lastLocation)

    ASTValue.Call(
      ASTValue.NameReference("self", Some(location)),
      "define",
      ASTArguments.positional(Seq(
        ASTValue.String(name.string, Some(name.location)),
        value
      )),
      mayBeVarCall = false,
      Some(wholeDefLocation)
    )
  }

  private def addSelfAndTypeToFn(fn: ASTValue.Function, returnType: Option[ASTValue]) = {
    val classTypeCall = ASTValue.Call(
      ASTValue.NameReference("self", fn.location),
      "classType",
      ASTArguments.empty,
      mayBeVarCall = false,
      fn.location
    )

    ASTValue.Function(
      params = Seq(ASTParameter("self", Some(classTypeCall), fn.location)) ++ fn.params,
      body = fn.body,
      returnType = returnType.orElse(fn.returnType),
      location = fn.location
    )
  }
}
