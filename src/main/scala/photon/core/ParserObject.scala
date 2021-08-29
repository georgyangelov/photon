package photon.core

import photon.frontend.{ASTToValue, ASTValue, Parser, StaticScope, Token}
import photon.{FunctionTrait, RealValue, Value}

case class MacroASTValue(ast: ASTValue) extends NativeObject(Map.empty) {
  override val isFullyEvaluated = false
}

case class MetaValueObject(ast: ASTValue) extends NativeObject(Map(
  "#" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.Real(RealValue.Native(MacroASTValue(ast)), l) }
  ),

  "eval" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.Real(RealValue.Native(MacroASTValue(ast)), l) }
  )
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.Real(RealValue.String(token.string), l) }
  ),

  "type" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.Real(RealValue.String(token.tokenType.name), l) }
  )
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parseNext" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => Value.Real(RealValue.Native(MetaValueObject(parser.parseNext())), l) }
  ),

  "skipNextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => parser.skipNextToken(); Value.Real(RealValue.Nothing, l) }
  ),

  "nextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => Value.Real(RealValue.Native(TokenObject(parser.token)), l) }
  )
))
