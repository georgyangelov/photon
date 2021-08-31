package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEval, expectEvalCompileTime}

class MacroTest extends FunSuite {
  test("macro functions do not collide with functions in scope") {
    val macroDef = """
      Core.defineMacro 'objectify', (parser) {
        Struct(value = parser.parseNext.eval)
      }
    """

    expectEval(
      macroDef,
      """
        Struct = 1234

        (objectify 42).value
      """,
      "42"
    )
  }

  test("macro variables in lambda params do not collide with in-scope variables") {
    val macroDefinition = """
        Core.defineMacro 'run', (parser) {
          (variable) {
            variable.call
          }(parser.parseNext.eval)
        }
    """

    expectEval(
      macroDefinition,
      """
         answer = (){ 42 }.runTimeOnly
         variable = answer()

         run { variable }
      """,
      "42"
    )
  }

  test("supports simple parser macros") {
    val macroDefinition = """
        Core.defineMacro 'plusOne', (parser) {
          parser.parseNext.eval + 1
        }
    """

    expectEvalCompileTime(macroDefinition, "plusOne 41","42")
    expectEvalCompileTime(
      macroDefinition,
      "unknown = () { 41 }.runTimeOnly; plusOne unknown()",
      "unknown = () { 41 }; unknown() + 1"
    )
  }

  test("supports simple parser macros with lets") {
    val macroDefinition = """
        Core.defineMacro 'plusOne', (parser) {
          number = parser.parseNext.eval

          number + 1
        }
    """

    expectEvalCompileTime(macroDefinition, "plusOne 41","42")
    expectEvalCompileTime(
      macroDefinition,
      "unknown = () { 41 }.runTimeOnly; plusOne unknown()",
      "unknown = () { 41 }; plusOne$number = unknown(); plusOne$number + 1"
    )
  }

  test("supports parser macros") {
    val macroDefinition = """
        Core.defineMacro 'if', (parser) {
          condition = parser.parseNext
          ifTrue = parser.parseNext
          ifFalse = (parser.nextToken.string == "else").ifElse({ parser.skipNextToken; parser.parseNext.eval }, { {} })

          condition.eval.toBool.ifElse(ifTrue.eval, ifFalse)
        }
    """

    expectEvalCompileTime(macroDefinition, "if true { 42 }","42")
    expectEvalCompileTime(macroDefinition, "if true { 42 } else { 11 }","42")
    expectEvalCompileTime(macroDefinition, "if false { 42 } else { 11 }","11")

    expectEvalCompileTime(
      macroDefinition,
      "unknown = (){ true }.runTimeOnly; if unknown() { 42 } else { 11 }",
      "unknown = (){ true }; if$ifFalse = { 11 }; unknown().toBool.ifElse({ 42 }, if$ifFalse)"
    )
  }

  test("lambdas parsed by macros can use closure scope") {
    val macroDefinition = """
        Core.defineMacro 'run', (parser) {
          lambda = parser.parseNext

          lambda.eval.call
        }
    """

    expectEvalCompileTime(macroDefinition, "run { 42 }","42")
    expectEvalCompileTime(macroDefinition, "answer = 42; run { answer }", "42")
  }

  test("macro variables do not collide with in-scope variables") {
    val macroDefinition = """
        Core.defineMacro 'run', (parser) {
          variable = parser.parseNext.eval

          variable.call
        }
    """

    expectEval(
      macroDefinition,
      """
         answer = (){ 42 }.runTimeOnly
         variable = answer()

         run { variable }
      """,
      "42"
    )
  }
}
