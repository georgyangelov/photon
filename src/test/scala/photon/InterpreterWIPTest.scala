package photon

import org.scalatest._

import photon.TestHelpers._

class InterpreterWIPTest extends FunSuite {
  test("supports constants") {
    expectEvalRuntime("42", "42")
    expectEvalRuntime("'test'", "'test'")
  }

  test("can add numbers") {
    expectEvalRuntime("1 + 41", "42")
  }

  test("supports closures") {
    expectEvalRuntime("(a){ (b){ a + b } }(1)(41)", "42")
    expectEvalRuntime("a = 1; fn = (b) a + b; fn(41)", "42")
    expectEvalRuntime("a = 1; fn = (b) a + b; fn.call(41)", "42")
  }

  test("supports recursive functions") {
    expectEvalRuntime(
      "factorial = (n) { (n == 0).if_else({ 1 }, { n * factorial(n - 1) }) }; factorial(1)",
      "1"
    )
  }

  test("supports structs") {
    expectEvalRuntime(
      "user = Struct(name = 'Joro'); user.name",
      "'Joro'"
    )
    expectEvalRuntime(
      "computer = Struct(answer = 1 + 41); computer.answer",
      "42"
    )
  }

  test("supports constants compile-time") {
    expectEvalCompileTime("42", "42")
    expectEvalCompileTime("'test'", "'test'")
  }

  test("supports compile-time let") {
    expectEvalCompileTime("a = 42; a", "42")
    expectEvalCompileTime("a = 42; b = a; b", "42")
    expectEvalCompileTime("a = 41; b = 1; a + b", "42")
  }

  test("can add numbers compile-time") {
    expectEvalCompileTime("41 + 1", "42")
  }

  test("can call compile-time functions") {
    expectEvalCompileTime("add = (a, b) { a + b }; add(1, 41)", "42")
  }

  test("does not evaluate runtime-only functions during compile-time") {
    expectEvalCompileTime(
      "add = (a, b) { a + b }.runTimeOnly; add(1, 41)",
      "add = (a, b) { a + b }; add(1, 41)"
    )
  }

  test("does not try to compile-time evaluate functions with some unknown arguments") {
    expectEvalCompileTime(
      """
          unknown = () { 42 }
          add = (a, b) { a + b }
          var1 = unknown()
          var2 = 11

          add(var1, var2)
      """,
      "53"
    )

    expectEvalCompileTime(
      """
          unknown = () { 42 }.runTimeOnly
          add = (a, b) { a + b }
          var1 = unknown()
          var2 = 11

          add(var1, var2)
      """,
      """
          unknown = () { 42 }
          add = (a, b) { a + b }
          var1 = unknown()
          var2 = 11

          add(var1, var2)
      """
    )
  }

  test("evaluates some functions and leaves others during compile-time") {
    expectEvalCompileTime(
      """
          unknown = () { 42 }.runTimeOnly
          add = (a, b) { a + b }
          var1 = unknown()
          var2 = add(1, 10)

          add(var1, var2)
      """,
      """
          unknown = () { 42 }
          add = (a, b) { a + b }
          var1 = unknown()
          var2 = 11

          add(var1, var2)
      """
    )
  }

  test("evaluates some functions compile-time inside of lambdas during compile-time") {
    expectEvalCompileTime(
      """
          () {
            unknown = () { 42 }.runTimeOnly
            add = (a, b) { a + b }
            var1 = unknown()
            var2 = add(1, 10)

            add(var1, var2)
          }
      """,
      """
          () {
            unknown = () { 42 }
            add = (a, b) { a + b }
            var1 = unknown()
            var2 = 11

            add(var1, var2)
          }
      """
    )
  }

  test("supports simple parser macros") {
    val macroDefinition = """
        Core.define_macro 'plusOne', (parser) {
          parser.parse_next.eval + 1
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
        Core.define_macro 'plusOne', (parser) {
          number = parser.parse_next.eval

          number + 1
        }
    """

    expectEvalCompileTime(macroDefinition, "plusOne 41","42")
    expectEvalCompileTime(
      macroDefinition,
      "unknown = () { 41 }.runTimeOnly; plusOne unknown()",
      "unknown = () { 41 }; number = unknown(); number + 1"
    )
  }

//  test("supports parser macros") {
//    val macroDefinition = """
//        Core.define_macro 'if', (parser) {
//          condition = parser.parse_next
//          if_true = parser.parse_next
//          if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })
//
//          condition.eval.to_bool.if_else(if_true.eval, if_false)
//        }
//    """
//
//    expectEvalCompileTime(macroDefinition, "if true { 42 }","42")
//    expectEvalCompileTime(macroDefinition, "if true { 42 } else { 11 }","42")
//    expectEvalCompileTime(macroDefinition, "if false { 42 } else { 11 }","11")
//
//    expectEvalCompileTime(
//      macroDefinition,
//      "unknown = (){ true }.runTimeOnly; if unknown() { 42 } else { 11 }",
//      "unknown = (){ true }; unknown().to_bool.if_else({ 42 }, { 11 })"
//    )
//  }

//  TODO
//  test("breaks when let references itself directly") {
//    expectRuntimeFail(
//      "factorial = factorial; factorial(1)",
//      ""
//    );
//  }
}
