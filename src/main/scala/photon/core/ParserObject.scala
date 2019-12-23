package photon.core

import photon.{Parser, Value}

case class MetaValueObject(value: Value) extends NativeObject(Map(
  "#" -> { (c, args, l) => value },
  "eval" -> { (c, args, l) => value }
))

case class ParserObject(parser: Parser) extends NativeObject(Map(
  "parse_one" -> { (c, args, l) => Value.Native(MetaValueObject(parser.parseOne()), None) }
))
