package photon

import org.scalatest._

import photon.TestHelpers._

class CompileTimeEvaluationTest extends FunSuite {
  test("supports closures") {
    expectEvalCompileTime("(a: Int) { (b:Int) { a + b } }(1)(41)", "42")
    expectEvalCompileTime("val a = 1; val fn = (b:Int):Int a + b; fn(41)", "42")
    expectEvalCompileTime("val a = 1; val fn = (b:Int):Int a + b; fn.call(41)", "42")
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
    expectEvalCompileTime("val a = 42; a", "42")
    expectEvalCompileTime("val a = 42; val b = a; b", "42")
    expectEvalCompileTime("val a = 41; val b = 1; a + b", "42")
  }

  test("can add numbers compile-time") {
    expectEvalCompileTime("41 + 1", "42")
  }

  test("can call compile-time functions") {
    expectEvalCompileTime("val add = (a:Int, b:Int) { a + b }; add(1, 41)", "42")
  }

  test("does not evaluate runtime-only functions during compile-time") {
    expectEvalCompileTime(
      "val add = (a:Int, b:Int) { a + b }.runTimeOnly; add(1, 41)",
      "val add = (a:Int, b:Int): Int { a + b }; add(1, 41)"
    )
  }

  test("does not try to compile-time evaluate functions with some unknown arguments") {
    expectEvalCompileTime(
      """
          val unknown = { 42 }
          val add = (a:Int, b:Int):Int { a + b }
          val var1 = unknown()
          val var2 = 11

          add(var1, var2)
      """,
      "53"
    )

    expectEvalCompileTime(
      """
          val unknown = { 42 }.runTimeOnly
          val add = (a:Int, b:Int):Int { a + b }
          val var1 = unknown()
          val var2 = 11

          add(var1, var2)
      """,
      """
          val unknown = (): Int { 42 }
          val var1 = unknown()
          val var2 = 11

          var1 + var2
      """
    )
  }

  // TODO: This needs partial evaluation
  test("does not duplicate variables during partial evaluation") {
    expectEvalCompileTime(
      """
          val unknown = { 42 }.runTimeOnly
          val add = (a:Int, b:Int):Int { a + b }
          val var1 = unknown()
          val var2 = 11

          add(var1, var2) + var1
      """,
      """
          val unknown = (): Int { 42 }
          val var1 = unknown()
          val var2 = 11

          var1 + var2 + var1
      """
    )
  }

  test("does not duplicate variables during calls") {
    expectEvalCompileTime(
      """
          val unknown = { 42 }.runTimeOnly
          val add = (a:Int, b:Int) { a + b }
          val var1 = unknown()
          val var2 = 11

          var1 + var2
          add(var1, var2)
      """,
      """
          val unknown = (): Int { 42 }
          val var1 = unknown()
          val var2 = 11

          var1 + var2
          var1 + var2
      """
    )
  }

  test("evaluates some functions and leaves others during compile-time") {
    expectEvalCompileTime(
      """
          val unknown = { 42 }.runTimeOnly
          val add = (a:Int, b:Int) { a + b }
          val var1 = unknown()
          val var2 = add(1, 10)

          add(var1, var2)
      """,
      """
          val unknown = ():Int { 42 }
          val var1 = unknown()
          val var2 = 11

          var1 + var2
      """
    )
  }

  test("removes unused let bindings, keeping expressions for side-effects") {
    expectEvalCompileTime("val unused = 11; 42", "42")
    expectEvalCompileTime(
      "val unknown = ():Int{}.runTimeOnly; val something = unknown(); 42",
      "val unknown = ():Int{}; unknown(); 42"
    )
    expectEvalCompileTime(
      "val usedOnce = (a:Int):Int { 41 + a }; val result = usedOnce(1); result",
      "42"
    )
  }

  test("does not eliminate lets used by inner lambdas in params") {
    expectEvalCompileTime(
      "val unknown = ():Int{}.runTimeOnly; ():Int { unknown() }",
      "val unknown = ():Int{}; ():Int { unknown() }"
    )

    expectEvalCompileTime(
      "val unknown = (){ 42 }.runTimeOnly; (a: Function(returns=Int)) { a.call }(() unknown())",
      "val unknown = ():Int 42; unknown()"
    )
  }

  test("variables are kept if the value is unknown and uses them") {
    expectEvalCompileTime(
      "val a = 42; { a }",
      "val a = 42; (): Int { a }"
    )

    expectEvalCompileTime(
      "val a = 42; val unknown = { a }.runTimeOnly; unknown()",
      "val a = 42; val unknown = (): Int { a }; unknown()"
    )

    expectEvalCompileTime(
      "():Int { val a = 42; { a } }()",
      "val a = 42; (): Int { a }"
    )
  }

  test("variables do not escape the scope (without partial evaluation)") {
    expectEvalCompileTime(
      "outer = (a:Int) { { a } }; outer(42)",
      "val a = 42; (): Int { a }"
    )

    expectEvalCompileTime(
      "val a = 11; val outer = (a:Int) { { a } }; outer(a + 31)",
      "val a = 42; (): Int { a }"
    )

    expectEvalCompileTime(
      "val a = 11; val outer = (a:Int) { { a } }; outer(42); a",
      //      "a = 11; (a = 42; () { a }); a"
      "11"
    )
  }

  test("variables do not escape the scope (without partial evaluation) 2") {
    expectEvalCompileTime(
      "val fn = (a:Int) { { a } }; val something = (x:Function(returns=Int)) { x }.runTimeOnly; something(x = fn(42))",
      "val something = (x:Function(returns=Int)): Function(returns=Int) { x }; something(x = (a = 42; (): Int { a }))"
    )
  }

  test("variables do not escape the scope (without partial evaluation) 3") {
    expectEvalCompileTime(
      """
        val scope1 = (a:Int) {
          val unknown = { 42 }.runTimeOnly

          { a + unknown() }
        }

        scope1(1)
      """,
      """
        val a = 1
        val unknown = (): Int 42

        (): Int { a + unknown() }
      """
    )
  }

  test("variables do not escape the scope (without partial evaluation) 4") {
    expectEvalCompileTime(
      """
        val scope1 = (a:Int) {
          val unknown = { a + 42 }.runTimeOnly

          { a + unknown() }
        }

        scope1(1)
      """,
      """
        val a = 1
        val unknown = ():Int { a + 42 }

        ():Int { a + unknown() }
      """
    )
  }

  test("scope escapes with inner lets") {
    expectEvalCompileTime(
      """
        val outer = (a:Int) {
          val inner = (
            val b = 11
            val inner2 = { a + b }
            inner2
          )

          inner
        }

        outer(42)
      """,
      """
        val a = 42
        val b = 11
        ():Int { a + b }
      """
    )
  }

  test("variables do not escape the scope") {
    expectEvalCompileTime(
      "val outer = (a:Int) { { a } }; outer(42)",
      "val a = 42; (): Int { a }"
    )

    expectEvalCompileTime(
      "val unknown = { 42 }.runTimeOnly; val outer = { { unknown() } }; outer()",
      "val unknown = ():Int 42; ():Int { unknown() }"
    )
  }

//    TODO: This probably needs to happen at some point, but it's fine for now.
//          The problem is that to do this, we need to track and preserve the scopes correctly.
  test("does not break on evaluating nested lambdas") {
    expectEvalCompileTime(
      """
        val unknown = { 42 }.runTimeOnly

        val scope1 = (a: Int) {
          { a + unknown() }
        }

        val scope2 = (a: Int) {
          scope1(42)
        }

        scope2(1)
      """,
      """
        val unknown = ():Int 42
        val a = 42

        (): Int { a + unknown() }
      """
    )
  }

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
