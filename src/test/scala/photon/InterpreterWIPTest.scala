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

//  TODO
//  test("breaks when let references itself directly") {
//    expectRuntimeFail(
//      "factorial = factorial; factorial(1)",
//      ""
//    );
//  }
}
