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
        val Struct = 1234

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
         val answer = (){ 42 }.runTimeOnly
         val variable = answer()

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
      "val unknown = () { 41 }.runTimeOnly; plusOne unknown()",
      "val unknown = () { 41 }; unknown() + 1"
    )
  }

  test("supports simple parser macros with lets") {
    val macroDefinition = """
        Core.defineMacro 'plusOne', (parser) {
          val number = parser.parseNext.eval

          number + 1
        }
    """

    expectEvalCompileTime(macroDefinition, "plusOne 41","42")
    expectEvalCompileTime(
      macroDefinition,
      "val unknown = () { 41 }.runTimeOnly; plusOne unknown()",
      "val unknown = () { 41 }; val plusOne$number = unknown(); plusOne$number + 1"
    )
  }

  test("supports parser macros") {
    val macroDefinition = """
        Core.defineMacro 'if', (parser) {
          val condition = parser.parseNext
          val ifTrue = parser.parseNext
          val ifFalse = (parser.nextToken.string == "else").ifElse({ parser.skipNextToken; parser.parseNext.eval }, { {} })

          condition.eval.toBool.ifElse(ifTrue.eval, ifFalse)
        }
    """

    expectEvalCompileTime(macroDefinition, "if true { 42 }","42")
    expectEvalCompileTime(macroDefinition, "if true { 42 } else { 11 }","42")
    expectEvalCompileTime(macroDefinition, "if false { 42 } else { 11 }","11")

    expectEvalCompileTime(
      macroDefinition,
      "val unknown = (){ true }.runTimeOnly; if unknown() { 42 } else { 11 }",
      "val unknown = (){ true }; val if$ifFalse = { 11 }; unknown().toBool.ifElse({ 42 }, if$ifFalse)"
    )
  }

  test("lambdas parsed by macros can use closure scope") {
    val macroDefinition = """
        Core.defineMacro 'run', (parser) {
          val lambda = parser.parseNext

          lambda.eval.call
        }
    """

    expectEvalCompileTime(macroDefinition, "run { 42 }","42")
    expectEvalCompileTime(macroDefinition, "val answer = 42; run { answer }", "42")
  }

  test("macro variables do not collide with in-scope variables") {
    val macroDefinition = """
        Core.defineMacro 'run', (parser) {
          val variable = parser.parseNext.eval

          variable.call
        }
    """

    expectEval(
      macroDefinition,
      """
         val answer = (){ 42 }.runTimeOnly
         val variable = answer()

         run { variable }
      """,
      "42"
    )
  }
}
