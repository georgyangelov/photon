package photon

import org.scalatest._
import photon.support.TestHelpers._

class InterpreterTest extends FunSuite {
  test("can eval simple values") {
    expectCompileTime("42", "42")
  }

  test("can call native methods") {
    expectCompileTime("1 + 41", "42")
    expectCompileTime("1 + 40 + 1 + 1 - 1", "42")
  }

  test("can call lambdas") {
    expectCompileTime("():Int{ 42 }()", "42")
    expectCompileTime("():Int{ 42 }.call", "42")
    expectCompileTime("(a:Int):Int{ a + 41 }(1)", "42")
    expectCompileTime("(a:Int):Int{ a + 41 }.call 1", "42")
  }

  test("assignment") {
    expectCompileTime("val answer = 42; answer", "42")
  }

  test("return type inference") {
    expectCompileTime("(a: Int) { a + 41 }.call 1", "42")
    // expectCompileTime("(a: Int) { a + 41 }.returnType", "Int")
  }

  test("closures") {
    expectCompileTime("(a:Int){ (b:Int){ a + b } }(1)(41)", "42")
  }

  test("higher-order function types") {
    expectCompileTime(
      """
        val IntFn = (a: Int): Int

        val callWithOne = (fn: IntFn) { fn(1) }
        callWithOne (a: Int) { 41 + a }
      """,
      "42"
    )
  }

  test("higher-order functions") {
    expectCompileTime(
      """
        val hof = (fn: (a: Int): Int):Int { fn(1) }
        val fn = (a: Int): Int { a + 41 }

        hof(fn)
      """,
      "42"
    )

//    expectCompileTime("(fn:Int):Int{ fn(1) }((a:Int):Int{ val b = a; b + 41 })", "42")
  }

  ignore("simple macros") {
    expectCompileTime(
      "Core.defineMacro('add_one', (parser: Parser): Any { val e = parser.parseNext(); #e + 1 })",
      "val unknown = ():Int{}.runTimeOnly; add_one unknown()",

      "val unknown = ():Int{}; unknown() + 1"
    )

    expectCompileTime(
      "Core.defineMacro('add_one', (parser: Parser): Any { val e = parser.parseNext(); #e + 1 })",
      "(a:Int):Int{ add_one(a + 2) }",

      "(a:Int):Int{ a + 2 + 1 }"
    )

    expectCompileTime(
      "Core.defineMacro('add_one', (parser: Parser): Any { val e = parser.parseNext(); 42 })",
      "(a:Int):Int{ add_one(a + 2) }",

      "(a:Int):Int{ 42 }"
    )
  }

  test("named arguments") {
    expectCompileTime("(a:Int):Int { 41 + a }(a = 1)", "42")
    expectCompileTime("(a:Int, b:Int):Int { 41 - a + b }(1, b = 2)", "42")
    expectCompileTime("(a:Int, b:Int, c:Int):Int { (20 - a + b) * c }(1, b = 2, c = 2)", "42")
  }

  ignore("runtime-only functions") {
    expectPhases("val runtime = { 42 }; runtime()", "42", "42")
    expectPhases("val runtime = { 42 }.runTimeOnly; runtime()", "val runtime = ():Int { 42 }; runtime()", "42")
  }

  ignore("compile-time-only functions") {
    expectPhases("val fn = ():Int { 42 }.compileTimeOnly; fn()", "42", "42")
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
//    expectCompileTime("a = Ref(42); a.get", "a = Ref(42); a.get")
//    expectCompileTime("a = Ref(1); a.set(42); a.get", "42")
//  }

  ignore("binding to variable names in functions") {
    expectCompileTime("val factorial = (n:Int):Int { (n == 1).ifElse { 1 }, { n * factorial(n - 1) } }; factorial 5", "120")
  }
}
