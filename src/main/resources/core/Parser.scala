package photon.core

import photon.New.CompileTimeOnlyMethod
import photon.frontend.{ASTValue, Parser, Token}
import photon.{ArgumentType, Arguments, Location, MethodType, New, PureValue, RealValue, TypeType, UnknownType}
import photon.core.Conversions._
import photon.interpreter.CallContext

case class ParserObject(parser: Parser) extends New.NativeObject(ParserType)
object ParserType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map(
    "parseNext" -> new CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "parseNext",
        arguments = Seq.empty,
        returns = ASTValueType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ParserObject(parser) = args.getNativeSelf[ParserObject]

        PureValue.Native(ASTValueObject(parser.parseNext()), location)
      }
    },

    "nextToken" -> new CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "nextToken",
        arguments = Seq.empty,
        returns = TokenType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ParserObject(parser) = args.getNativeSelf[ParserObject]

        PureValue.Native(TokenObject(parser.token), location)
      }
    },

    "skipNextToken" -> new CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "skipNextToken",
        arguments = Seq.empty,
        returns = NothingType
      )

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
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "string",
        arguments = Seq.empty,
        returns = StringType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val TokenObject(token) = args.getNativeSelf[TokenObject]

        PureValue.String(token.string, location)
      }
    },

    "type" -> new CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "type",
        arguments = Seq.empty,
        returns = StringType
      )

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
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "#",
        arguments = Seq.empty,
        returns = UnknownType
      )

      override def call(context: CallContext, args: Arguments[RealValue], location: Option[Location]) = {
        val ASTValueObject(ast) = args.getNativeSelf[ASTValueObject]

        PureValue.Native(MacroASTValue(ast), location)
      }
    },

    "eval" -> new CompileTimeOnlyMethod {
      override def methodType(_argTypes: Arguments[New.TypeObject]) = MethodType(
        name = "eval",
        arguments = Seq.empty,
        returns = UnknownType
      )

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
