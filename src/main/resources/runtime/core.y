Core.define_macro 'if', (parser) {
  condition = parser.parseNext
  if_true = parser.parseNext
  if_false = (parser.nextToken.string == "else").if_else({ parser.skipNextToken; parser.parseNext.eval }, { {} })

  condition.eval.to_bool.if_else(if_true.eval, if_false)
}