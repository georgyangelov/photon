package photon

import org.scalatest._

import photon.TestHelpers._

class CompileTimeEvaluationTest extends FunSuite {
  test("supports closures") {
    expectEvalCompileTime("(a){ (b){ a + b } }(1)(41)", "42")
    expectEvalCompileTime("a = 1; fn = (b) a + b; fn(41)", "42")
    expectEvalCompileTime("a = 1; fn = (b) a + b; fn.call(41)", "42")
  }

//  TODO: Enable this to test
//  test("supports recursive functions") {
//    expectEvalCompileTime(
//      "factorial = (n) { (n == 0).ifElse({ 1 }, { n * factorial(n - 1) }) }; factorial(1)",
//      "1"
//    )
//  }

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

  test("removes unused let bindings, keeping expressions for side-effects") {
    expectEvalCompileTime("unused = 11; 42", "42")
    expectEvalCompileTime(
      "unknown = (){}.runTimeOnly; something = unknown(); 42",
      "unknown = (){}; unknown(); 42"
    )
    expectEvalCompileTime(
      "usedOnce = (a) { 41 + a }; result = usedOnce(1); result",
      "42"
    )
  }

  test("does not eliminate lets used by inner lambdas in params") {
    expectEvalCompileTime(
      "unknown = (){}.runTimeOnly; () { unknown() }",
      "unknown = (){}; () { unknown() }"
    )

//    TODO: This probably needs to happen at some point, but it's fine for now.
//          The problem is that to do this, we need to track and preserve the scopes correctly.
//    expectEvalCompileTime(
//      "unknown = (){}.runTimeOnly; (a) { a.call }(() { unknown() })",
//      "unknown = (){}; unknown()"
//    )
  }

  test("variables are kept if the value is unknown and uses them") {
    expectEvalCompileTime(
      "a = 42; () { a }",
      "a = 42; () { a }"
    )

    expectEvalCompileTime(
      "a = 42; unknown = () { a }.runTimeOnly; unknown()",
      "a = 42; unknown = () { a }; unknown()"
    )

    expectEvalCompileTime(
      "() { a = 42; () { a } }()",
      "a = 42; () { a }"
    )
  }

  test("variables do not escape the scope (without partial evaluation)") {
    expectEvalCompileTime(
      "outer = (a) { () { a } }; outer(42)",
      "a = 42; () { a }"
    )

    expectEvalCompileTime(
      "a = 11; outer = (a) { () { a } }; outer(a + 31)",
      "a = 42; () { a }"
    )

    expectEvalCompileTime(
      "a = 11; outer = (a) { () { a } }; outer(42); a",
//      "a = 11; (a = 42; () { a }); a"
      "11"
    )

    expectEvalCompileTime(
      "fn = (a) { () { a } }; something = (x) { x }.runTimeOnly; something(param = fn(42))",
      "something = (x) { x }; something(param = (a = 42; () { a }))"
    )

    expectEvalCompileTime(
      """
        scope1 = (a) {
          unknown = () { 42 }.runTimeOnly

          () { a + unknown() }
        }

        scope1(1)
      """,
      """
        a = 1
        unknown = () 42

        () { a + unknown() }
      """
    )

    expectEvalCompileTime(
      """
        scope1 = (a) {
          unknown = () { a + 42 }.runTimeOnly

          () { a + unknown() }
        }

        scope1(1)
      """,
      """
        a = 1
        unknown = () a + 42

        () { a + unknown() }
      """
    )
  }

  test("scope escapes with inner lets") {
    expectEvalCompileTime(
      """
        outer = (a) {
          inner = (
            b = 11
            inner2 = () { a + b }
            inner2
          )

          inner
        }

        outer(42)
      """,
      """
        a = 42
        b = 11
        () { a + b }
      """
    )
  }

  test("variables do not escape the scope") {
    expectEvalCompileTime(
      "outer = (a) { () { a } }; outer(42)",
      "a = 42; () { a }"
    )

//    TODO: This probably needs to happen at some point, but it's fine for now.
//          The problem is that to do this, we need to track and preserve the scopes correctly.
//    expectEvalCompileTime(
//      "unknown = (){}.runTimeOnly; outer = () { () { unknown() } }; outer()",
//      "unknown = (){}; () { unknown() }"
//    )

    expectEvalCompileTime(
      """
        scope1 = (a) {
          unknown = () { 42 }.runTimeOnly

          () { a + unknown() }
        }

        scope1(1)
      """,
      """
        a = 1
        unknown = () 42

        () { a + unknown() }
      """
    )
  }

//    TODO: This probably needs to happen at some point, but it's fine for now.
//          The problem is that to do this, we need to track and preserve the scopes correctly.
//  test("does not break on evaluating nested lambdas") {
//    expectEvalCompileTime(
//      """
//        unknown = (){}.runTimeOnly
//
//        scope1 = (a) {
//          () { a + unknown() }
//        }
//
//        scope2 = (a) {
//          scope1(42)
//        }
//
//        scope2(1)
//      """,
//      """
//        unknown = (){}
//
//        scope2 = (a) {
//          () { 42 + unknown() }
//        }
//
//        scope2(1)
//      """
//    )
//  }

//  test("allows for variable redefinition and usage of the parent variable in let") {
//    expectEvalCompileTime(
//      "answer = 41; answer = answer + 1; answer",
//      "42"
//    )
//  }

//  test("breaks when let references itself directly") {
//    expectRuntimeFail(
//      "factorial = factorial; factorial(1)",
//      "Cannot directly reference a variable in its definition"
//    );
//  }
}
