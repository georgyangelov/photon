package photon.core

import photon.frontend.{ASTValue, Parser, Token}
import photon.{Arguments, FunctionTrait, Location, PureValue, RealValue, Value}

case class MacroASTValue(ast: ASTValue) extends NativeObject(Map.empty) {
  override val isFullyEvaluated = false
}

case class MetaValueObject(ast: ASTValue) extends NativeObject(Map(
  "#" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Native(MacroASTValue(ast), location)
  },

  "eval" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Native(MacroASTValue(ast), location)
  }
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.String(token.string, location)
  },

  "type" -> new {} with PureMethod {
    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.String(token.tokenType.name, location)
  }
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parseNext" -> new {} with NativeMethod {
    override val traits = Set(FunctionTrait.CompileTime)

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Native(MetaValueObject(parser.parseNext()), location)

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
      throw new NotImplementedError("Cannot call parseNext partially")
  },

  "skipNextToken" -> new {} with NativeMethod {
    override val traits = Set(FunctionTrait.CompileTime)

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
      parser.skipNextToken()

      PureValue.Nothing(location)
    }

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
  },

  "nextToken" -> new {} with NativeMethod {
    override val traits = Set(FunctionTrait.CompileTime)

    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
      PureValue.Native(TokenObject(parser.token), location)

    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
  }
))
