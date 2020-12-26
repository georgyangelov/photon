package photon.core

import photon.{Parser, Token, Value}

case class MetaValueObject(value: Value) extends NativeObject(Map(
  "#" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => value }
  ),

  "eval" -> ScalaMethod(
    MethodOptions(Seq.empty),
    { (c, args, l) => value }
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
  "parse_next" -> ScalaMethod(
    MethodOptions(Seq.empty, withSideEffects = true),
    { (c, args, l) => Value.Native(MetaValueObject(parser.parseNext()), None) }
  ),

  "token" -> ScalaMethod(
    MethodOptions(Seq.empty, withSideEffects = true),
    { (c, args, l) => Value.Native(TokenObject(parser.token), l) }
  )
))
