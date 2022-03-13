package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEvalCompileTime, expectFailCompileTime}

class PartialEvaluationTest extends FunSuite {
  test("evaluates functions partially if possible") {
    expectEvalCompileTime(
      """
          () {
            val unknown = { 42 }.runTimeOnly
            val add = (a: Int, b: Int) a + b
            val var1 = unknown()
            val var2 = add(1, 10)

            add(var1, var2)
          }()
      """,
      """
          val unknown = (): Int 42
          val var1 = unknown()
          val var2 = 11

          var1 + var2
      """
    )

    expectEvalCompileTime(
      """
          val arg = { 42 }.runTimeOnly

          (i: Int) {
            val add = (a: Int, b: Int) a + b
            val var2 = add(1, 10)

            add(i, var2)
          }(arg())
      """,
      """
          val arg = (): Int 42
          val i = arg()
          val var2 = 11

          i + var2
      """
    )
  }

  ignore("should not execute things out of order") {
    expectFailCompileTime(
      """
          {
            val compileTimeFn = (answer: Int) {
              Log.debug "Line 1", answer
              { Log.debug "Answer", 2 + 2 }.compileTimeOnly()()
              Log.debug "Line 2"
            }.compileTimeOnly

            compileTimeFn()
          }.runTimeOnly()()
        """,
      ""
    )
  }

  test("evaluates some functions compile-time inside of lambdas") {
    expectEvalCompileTime(
      """
          () {
            val unknown = { 42 }.runTimeOnly
            val add = (a: Int, b: Int) a + b
            val var1 = unknown()
            val var2 = add(1, 10)

            add(var1, var2)
          }
      """,
      """
          (): Int {
            val unknown = () 42
            val add = (a: Int, b: Int): Int a + b
            val var1 = unknown()
            val var2 = 11

            add(var1, var2)
          }
      """
    )
  }

  test("evaluates some functions compile-time inside of runtime-only lambdas") {
    expectEvalCompileTime(
      """
          () {
            val unknown = { 42 }.runTimeOnly
            val add = (a: Int, b: Int) a + b
            val var1 = unknown()
            val var2 = add(1, 10)

            add(var1, var2)
          }.runTimeOnly()()
      """,
      """
          (): Int {
            val unknown = () 42
            val add = (a: Int, b: Int): Int a + b
            val var1 = unknown()
            val var2 = 11

            add(var1, var2)
          }()
      """
    )
  }

  ignore("partial evaluation of simple objects") {
    expectEvalCompileTime(
      "val object = Object(unknown = () {}.runTimeOnly, answer = () 42); object.answer",
      "42"
    )
  }

  ignore("compile-time evaluation of partial objects") {
    expectEvalCompileTime(
      "val unknown = () { 11 }.runTimeOnly; val object = Object(unknown = unknown, answer = () 42); object.answer",
      "42"
    )
    expectEvalCompileTime(
      "val unknown = () { 42 }.runTimeOnly; val object = Object(unknown = unknown, answer = () 42); object.unknown",
      "val unknown = () { 42 }; val object = Object(unknown = unknown, answer = () 42); object.unknown"
    )
  }

  //  test("can partially evaluate code") {
  //    expectEvalCompileTime("(a) { 1 + 41 + a }", "(a) { 42 + a }")
  //    expectEvalCompileTime("(a) { a + (1 + 41) }", "(a) { a + 42 }")
  //  }
  //
  //  test("nested usages of variables") {
  //    expectEvalCompileTime("(a){ { { a } } }(42)", "{ { 42 } }")
  //    expectEvalCompileTime("(a){ { { a + 1 } } }(41)", "{ { 42 } }")
  //    expectEvalCompileTime("(a){ $? + { a } }(42)", "$? + { 42 }")
  //  }
  //
  //  //  test("partial evaluation should not inline multiple times") {
  //  //    expectEvalCompileTime("{ |a| { |b| a + b + a } }(42)", "{ |a| { |b| a + b + a } }(42)")
  //  //  }
  //
  //  test("evaluation of partial lambdas") {
  //    expectEvalCompileTime("(a){ (b){ a + b }(42) }", "(a){ a + 42 }")
  //  }
  //
  //  test("partial evaluation with unknowns") {
  //    expectEvalCompileTime("(a){ a + 1 + $? }(3)", "4 + $?")
  //    expectEvalCompileTime("a = 3; b = a + 1; a + b + $?", "7 + $?")
  //    expectEvalCompileTime("(fn){ fn(1) + fn(2) + $? }((a){ a + 1 })", "5 + $?")
  //    expectEvalCompileTime("inc = (a){ a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?", "9 + $?")
  //    expectEvalCompileTime("fn = (a){ a + 1 }; fn(1) + fn(2) + $?", "5 + $?")
  //  }
  //
  //  test("advanced partial evaluation") {
  //    expectEvalCompileTime("(a){ (b){ (c){ a + b + c }(2) }($?) }(1)", "(b){ 1 + b + 2 }($?)")
  //    expectEvalCompileTime("a = 1; b = $?; c = 2; a + b + c", "(b){ 1 + b + 2 }($?)")
  //  }
}
