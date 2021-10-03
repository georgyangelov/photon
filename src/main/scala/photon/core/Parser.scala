package photon.core

import photon.New.CompileTimeOnlyMethod
import photon.frontend.{ASTValue, Parser, Token}
import photon.{Arguments, Location, New, PureValue, RealValue, TypeType, UnknownType}
import photon.core.Conversions._
import photon.interpreter.CallContext

case class ParserObject(parser: Parser) extends New.NativeObject(ParserType)
object ParserType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map(
    "parseNext" -> new CompileTimeOnlyMethod {
      override val name = "parseNext"
      override val arguments = Seq.empty
      override val returns = ASTValueType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ParserObject(parser) = args.getNativeSelf[ParserObject]

        PureValue.Native(ASTValueObject(parser.parseNext()), location)
      }
    },

    "nextToken" -> new CompileTimeOnlyMethod {
      override val name = "nextToken"
      override val arguments = Seq.empty
      override val returns = TokenType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ParserObject(parser) = args.getNativeSelf[ParserObject]

        PureValue.Native(TokenObject(parser.token), location)
      }
    },

    "skipNextToken" -> new CompileTimeOnlyMethod {
      override val name = "skipNextToken"
      override val arguments = Seq.empty
      override val returns = NothingType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ParserObject(parser) = args.getNativeSelf[ParserObject]

        parser.skipNextToken()

        PureValue.Nothing(location)
      }
    }
  )
}

case class TokenObject(token: Token) extends New.NativeObject(TokenType)
object TokenType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map(
    "string" -> new CompileTimeOnlyMethod {
      override val name = "string"
      override val arguments = Seq.empty
      override val returns = StringType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val TokenObject(token) = args.getNativeSelf[TokenObject]

        PureValue.String(token.string, location)
      }
    },

    "type" -> new CompileTimeOnlyMethod {
      override val name = "type"
      override val arguments = Seq.empty
      override val returns = StringType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val TokenObject(token) = args.getNativeSelf[TokenObject]

        PureValue.String(token.tokenType.name, location)
      }
    }
  )
}

case class ASTValueObject(ast: ASTValue) extends New.NativeObject(ASTValueType)
case class MacroASTValue(ast: ASTValue) extends New.NativeObject(UnknownType) {
  override val isFullyEvaluated = false
}
object ASTValueType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map(
    // TODO: These are no longer "pure" methods
    "#" -> new CompileTimeOnlyMethod {
      override val name = "#"
      override val arguments = Seq.empty
      override val returns = UnknownType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ASTValueObject(ast) = args.getNativeSelf[ASTValueObject]

        PureValue.Native(MacroASTValue(ast), location)
      }
    },

    "eval" -> new CompileTimeOnlyMethod {
      override val name = "eval"
      override val arguments = Seq.empty
      override val returns = UnknownType

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ASTValueObject(ast) = args.getNativeSelf[ASTValueObject]

        PureValue.Native(MacroASTValue(ast), location)
      }
    }
  )
}


//case class MetaValueObject(ast: ASTValue) extends NativeObject(Map(
//  "#" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Native(MacroASTValue(ast), location)
//  },
//
//  "eval" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Native(MacroASTValue(ast), location)
//  }
//))

//case class TokenObject(token: Token) extends NativeObject(Map(
//  "string" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.String(token.string, location)
//  },
//
//  "type" -> new {} with PureMethod {
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.String(token.tokenType.name, location)
//  }
//))

//case class ParserObject(parser: Parser) extends NativeObject(Map(
//  "parseNext" -> new {} with NativeMethod {
//    override val traits = Set(FunctionTrait.CompileTime)
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Native(MetaValueObject(parser.parseNext()), location)
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) =
//      throw new NotImplementedError("Cannot call parseNext partially")
//  },
//
//  "skipNextToken" -> new {} with NativeMethod {
//    override val traits = Set(FunctionTrait.CompileTime)
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
//      parser.skipNextToken()
//
//      PureValue.Nothing(location)
//    }
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
//  },
//
//  "nextToken" -> new {} with NativeMethod {
//    override val traits = Set(FunctionTrait.CompileTime)
//
//    override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) =
//      PureValue.Native(TokenObject(parser.token), location)
//
//    override def partialCall(context: CallContext, args: Arguments[Value], location: Option[Location]) = ???
//  }
//))
