package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEvalCompileTime, expectFailCompileTime}

class TypeSystemTest extends FunSuite {
  test("calling functions with types") {
    expectEvalCompileTime("val fn = (a: Int): Int a + 41; fn(1)", "42")
    expectEvalCompileTime("val fn = (a: Int, b: Int): Int a + b; fn(1, 41)", "42")
  }

  test("assignment of variables with types") {
    expectEvalCompileTime("val answer: Int = 42; answer + 1", "43")
    expectFailCompileTime("val answer: String = 42", "Invalid value 42: Int for type String")
  }

  test("typechecking primitive types") {
    expectEvalCompileTime("42: Int", "42")
    expectFailCompileTime("42: String", "Invalid value 42: Int for type String")
  }

  test("typecheck classes") {
    expectEvalCompileTime(
      """
         class Person { def name: String; def age: Int }

         val person = Person.new(name = "a", age = 42)
         (person: Person).age
      """,
      "42"
    )
  }

  ignore("type inference for function arguments") {
    expectEvalCompileTime(
      "val fn = (a: Float) a + 1; fn(42: Int)",
      "43"
    )

    expectFailCompileTime(
      "val fn = (a: Int) a + 1; fn(42.1)",
      "aaaaa"
    )
  }

//  test("implicit conversions of numbers") {
//    expectEvalCompileTime(
//      "val answer: Float = 42: Int; answer",
//      "42"
//    )
//  }
}
