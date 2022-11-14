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
}
