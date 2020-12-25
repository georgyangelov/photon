package photon.core

import photon.{Parser, Token, Value}

case class MetaValueObject(value: Value) extends NativeObject(Map(
  "#" -> ScalaMethod({ (c, args, l) => value }),
  "eval" -> ScalaMethod({ (c, args, l) => value })
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> ScalaMethod({ (c, args, l) => Value.String(token.string, l) }),
  "type" -> ScalaMethod({ (c, args, l) => Value.String(token.tokenType.name, l) })
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parse_next" -> ScalaMethod(
    { (c, args, l) => Value.Native(MetaValueObject(parser.parseNext()), None) },
    withSideEffects = true
  ),

  "token" -> ScalaMethod(
    { (c, args, l) => Value.Native(TokenObject(parser.token), l) },
    withSideEffects = true
  )
))
