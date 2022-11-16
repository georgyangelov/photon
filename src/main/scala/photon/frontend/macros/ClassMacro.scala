package photon.frontend.macros

import photon.base.Location
import photon.frontend._

object ClassMacro {
  def classMacro(parser: Parser, location: Location): ASTValue =
    classBuilderMacro("Class", parser, location)

  def interfaceMacro(parser: Parser, location: Location): ASTValue =
    classBuilderMacro("Interface", parser, location)

  private def classBuilderMacro(buildType: String, parser: Parser, location: Location): ASTValue = {
    val (name, builderFn) = parser.token match {
      case Token(TokenType.Name, _, _, _) =>
        (
          Some(parser.readToken(TokenType.Name, s"Expected a name after $buildType")),
          parser.parseAST[ASTValue.Function]
        )
      case _ => (None, parser.parseAST[ASTValue.Function])
    }

    val builderFnWithSelfArg =
      ASTValue.Function(
        params = Seq(
          ASTParameter(
            "self",
            "self",
            Some(ASTValue.Pattern.SpecificValue(ASTValue.NameReference("ClassBuilder", Some(location)))),
            Some(location)
          )
        ),
        body = builderFn.body,

        // TODO: NothingType
        returnType = None,

        location = builderFn.location
      )

    val classDefLocation = location.extendWith(parser.lastLocation)

    name match {
      case Some(name) =>
        val block = parser.parseRestOfBlock()
        val letLocation = location.extendWith(parser.lastLocation)

        ASTValue.Let(
          name.string,
          ASTValue.Call(
            ASTValue.NameReference(buildType, Some(location)),
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

      case None =>
        ASTValue.Call(
          ASTValue.NameReference(buildType, Some(location)),
          "new",
          ASTArguments.positional(Seq(builderFnWithSelfArg)),
          mayBeVarCall = false,
          Some(classDefLocation)
        )
    }
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
      parser.parseNext() match {
        // def agePlus(x: Int) { age + 1 }
        case fn: ASTValue.Function => addSelfAndTypeToFn(fn, None)

        // def agePlus(x: Int): Int
        case fnType => fnType
      }
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
    val selfTypeCall = ASTValue.Call(
      ASTValue.NameReference("self", fn.location),
      "selfType",
      ASTArguments.empty,
      mayBeVarCall = false,
      fn.location
    )

    ASTValue.Function(
      params = Seq(ASTParameter("self", "self", Some(ASTValue.Pattern.SpecificValue(selfTypeCall)), fn.location)) ++ fn.params,
      body = fn.body,
      returnType = returnType.orElse(fn.returnType),
      location = fn.location
    )
  }
}
