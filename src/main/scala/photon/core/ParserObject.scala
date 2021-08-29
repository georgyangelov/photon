package photon.core

import photon.frontend.{ASTValue, Parser, Token}
import photon.{FunctionTrait, PureValue}

case class MacroASTValue(ast: ASTValue) extends NativeObject(Map.empty) {
  override val isFullyEvaluated = false
}

case class MetaValueObject(ast: ASTValue) extends NativeObject(Map(
  "#" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => PureValue.Native(MacroASTValue(ast), l) }
  ),

  "eval" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => PureValue.Native(MacroASTValue(ast), l) }
  )
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => PureValue.String(token.string, l) }
  ),

  "type" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => PureValue.String(token.tokenType.name, l) }
  )
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parseNext" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => PureValue.Native(MetaValueObject(parser.parseNext()), l) }
  ),

  "skipNextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => parser.skipNextToken(); PureValue.Nothing(l) }
  ),

  "nextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => PureValue.Native(TokenObject(parser.token), l) }
  )
))
