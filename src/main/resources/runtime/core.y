Core.define_macro "if", (parser) {
  condition = parser.parse_next
  if_true = parser.parse_next
  if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })

  condition.eval.to_bool.if_else(if_true.eval, if_false)
}
