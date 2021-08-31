Core.defineMacro 'if', (parser) {
  condition = parser.parseNext
  ifTrue = parser.parseNext
  ifFalse = (parser.nextToken.string == "else").ifElse({ parser.skipNextToken; parser.parseNext.eval }, { {} })

  condition.eval.toBool.ifElse(ifTrue.eval, ifFalse)
}
