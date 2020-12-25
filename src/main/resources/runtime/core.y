Core.define_macro "if", (parser) {
  condition = parser.parse_one
  if_true = parser.parse_one
  if_false = (parser.token.string == "else").if_else({ parser.parse_one.eval }, { {} })

  condition.eval.to_bool.if_else(if_true.eval, if_false)
}
