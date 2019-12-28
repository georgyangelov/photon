package photon.core

import photon.{Parser, Token, Value}

case class MetaValueObject(value: Value) extends NativeObject(Map(
  "#" -> { (c, args, l) => value },
  "eval" -> { (c, args, l) => value }
))

case class TokenObject(token: Token) extends NativeObject(Map(
  "string" -> { (c, args, l) => Value.String(token.string, l) },
  "type" -> { (c, args, l) => Value.String(token.tokenType.name, l) }
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parse_one" -> { (c, args, l) => Value.Native(MetaValueObject(parser.parseOne()), None) },
  "token" -> { (c, args, l) => Value.Native(TokenObject(parser.token), l) }
))
