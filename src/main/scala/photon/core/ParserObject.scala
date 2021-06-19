package photon.core

import photon.frontend.{ASTValue, Parser, Token}
import photon.{FunctionTrait, Value}

case class MetaValueObject(ast: ASTValue) extends NativeObject(Map(
  "#" -> ScalaMethod(
    MethodOptions(Seq.empty),
    // TODO: Implement this
    { (c, args, l) => Value.Unknown(l) }
  ),

  "eval" -> ScalaMethod(
    MethodOptions(Seq.empty),
    // TODO: Implement this
    { (c, args, l) => Value.Unknown(l) }
  )
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.String(token.string, l) }
  ),

  "type" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => Value.String(token.tokenType.name, l) }
  )
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parseNext" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => Value.Native(MetaValueObject(parser.parseNext()), l) }
  ),

  "skipNextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => parser.skipNextToken(); Value.Nothing(l) }
  ),

  "nextToken" -> ScalaMethod(
    MethodOptions(Seq.empty, traits = Set(FunctionTrait.CompileTime)),
    { (c, args, l) => Value.Native(TokenObject(parser.token), l) }
  )
))
