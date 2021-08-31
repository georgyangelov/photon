package photon

import org.scalatest._

import photon.TestHelpers._

class InterpreterTest extends FunSuite {
  test("can eval simple values") {
    expectEvalCompileTime("42", "42")
  }

  test("can call native methods") {
    expectEvalCompileTime("1 + 41", "42")
    expectEvalCompileTime("1 + 40 + 1 + 1 - 1", "42")
  }

  test("can call lambdas") {
    expectEvalCompileTime("{ 42 }()", "42")
    expectEvalCompileTime("{ 42 }.call", "42")
    expectEvalCompileTime("(a){ a + 41 }(1)", "42")
    expectEvalCompileTime("(a){ a + 41 }.call 1", "42")
  }

  test("assignment") {
    expectEvalCompileTime("answer = 42; answer", "42")
  }

  test("closures") {
    expectEvalCompileTime("(a){ (b){ a + b } }(1)(41)", "42")
  }

  test("higher-order functions") {
    expectEvalCompileTime("(fn){ fn(1) }((a){ a + 41 })", "42")
    expectEvalCompileTime("(fn){ fn(1) }((a){ b = a; b + 41 })", "42")
  }

  test("simple macros") {
    expectEvalCompileTime(
      "Core.defineMacro('add_one', (parser) { e = parser.parseNext(); #e + 1 })",
      "unknown = (){}.runTimeOnly; add_one unknown()",

      "unknown = (){}; unknown() + 1"
    )

    expectEvalCompileTime(
      "Core.defineMacro('add_one', (parser) { e = parser.parseNext(); #e + 1 })",
      "(a){ add_one(a + 2) }",

      "(a){ a + 2 + 1 }"
    )

    expectEvalCompileTime(
      "Core.defineMacro('add_one', (parser) { e = parser.parseNext(); 42 })",
      "(a){ add_one(a + 2) }",

      "(a){ 42 }"
    )
  }

  test("named arguments") {
    expectEvalCompileTime("(a) { 41 + a }(a = 1)", "42")
    expectEvalCompileTime("(a, b) { 41 - a + b }(1, b = 2)", "42")
    expectEvalCompileTime("(a, b, c) { (20 - a + b) * c }(1, b = 2, c = 2)", "42")
  }

  test("runtime-only functions") {
    expectPhases("runtime = () { 42 }; runtime()", "42", "42")
    expectPhases("runtime = () { 42 }.runTimeOnly; runtime()", "runtime = () { 42 }; runtime()", "42")
  }

  test("compile-time-only functions") {
    expectPhases("fn = () { 42 }.compileTimeOnly; fn()", "42", "42")
  }

// TODO: Make sure this is checked at some point with the type system
//  test("possibly evaluating compile-time function at runtime is an error") {
//    expectFail("(a) { fn = () { 42 }.compileTimeOnly; fn(a) }", "Could not evaluate compile-time-only function")
//    expectFail("runtime = () { 42 }; compileTime = (a) { a + 42 }.compileTimeOnly; compileTime(runtime())", "Could not evaluate compile-time-only function")
//
//    expectFail("fn = (a) { 42 }.compileTimeOnly; fn($?)", "Could not evaluate compile-time-only function")
//    expectFail("(a) { fn = (b) { 42 }.compileTimeOnly.call(a) }", "Could not evaluate compile-time-only function")
//  }

//  test("evaluating compile-time function at runtime is an error") {
//    expectRuntimeFail("runtime = () { 42 }.runTimeOnly; compileTime = (a) { a + 42 }.compileTimeOnly; compileTime(runtime())", "Could not evaluate compile-time-only function")
//  }

//  test("ref objects") {
//    expectEvalCompileTime("a = Ref(42); a.get", "a = Ref(42); a.get")
//    expectEvalCompileTime("a = Ref(1); a.set(42); a.get", "42")
//  }

  test("binding to variable names in functions") {
    expectEvalCompileTime("factorial = (n) { (n == 1).ifElse { 1 }, { n * factorial(n - 1) } }; factorial 5", "120")
  }
}
