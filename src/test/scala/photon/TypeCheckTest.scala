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

  ignore("checks function body") {
    expectTypeError("""
      val plusOne = (a: Boolean): Int a + 1

      plusOne(true)
    """)
  }

  ignore("checks generic function body on use") {
    expectTypeError("""
      val plusOne = (a: val T): Int a + 1

      plusOne(true)
    """)
  }
}
