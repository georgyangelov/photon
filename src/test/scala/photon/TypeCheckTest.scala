package photon

import org.scalatest._
import photon.support.TestHelpers._

class TypeCheckTest extends FunSuite {
  test("cannot convert between incompatible simple types") {
    expectTypeError("42: Boolean")
    expectTypeError("42: String")
    expectTypeError("\"answer\": Boolean")
    expectTypeError("\"answer\": Int")
    expectTypeError("true: Int")
    expectTypeError("true: String")
  }

  test("cannot convert between incompatible simple types in parameters") {
    expectTypeError("""
      val plusOne = (a: Int) a + 1

      plusOne(true)
    """)
  }

  test("type-checks during compile-time execution") {
    expectTypeError("""
      val badFn = (a: Boolean): Int a + 1

      badFn(true)
    """)
  }

  test("checks function body of uncalled functions") {
    expectTypeError("""
      (a: Boolean): Int a + 1
    """)
  }

  test("checks function body of nested uncalled functions") {
    expectTypeError("""
      {
        {
          (a: Boolean): Int a + 1
        }
      }
    """)
  }

  test("checks function body of uncalled methods of classes") {
    expectTypeError("""
      class Test {
        def a() 42
        def b(value: Boolean): Int value + 1
      }

      Test.new.a
    """)
  }

  test("checks function body of uncalled methods of interfaces") {
    expectTypeError("""
      interface Test {
        def a: Int
        def b(value: Boolean): Int value + 1
      }

      class Test2 {
        def a() 42
      }

      val test: Test = Test2.new
      test.a
    """)
  }

  ignore("checks function body of runTimeOnly functions") {
    expectTypeError("""
      val a = {
        (a: Boolean): Int a + 1
      }.runTimeOnly

      a()(true)
    """)
  }

  ignore("checks generic function body on use") {
    expectTypeError("""
      val plusOne = (a: val T): Int a + 1

      plusOne(true)
    """)
  }
}
