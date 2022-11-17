package photon

import org.scalatest._
import photon.support.TestHelpers._

class PatternArgumentsTest extends FunSuite {
  test("can use catch-all patterns for arguments") {
    expectCompileTime(
      """
        val test = (any: val T): Int 42

        test("asdf")
      """,
      "42"
    )
  }

  test("can use defined variables in return type") {
    expectCompileTime(
      """
        val identity = (value: val T): T { value }

        identity(42)
      """,
      "42"
    )
  }

  test("can assign generic functions to interfaces") {
    expectCompileTime(
      """
        interface IntIdentity {
          def call(value: Int): Int
        }

        val id: IntIdentity = (value: val T): T { value }
        id(42)
      """,
      "42"
    )

    expectCompileTime(
      """
        interface BooleanIdentity {
          def call(value: Boolean): Boolean
        }

        val id: BooleanIdentity = (value: val T): Boolean { true }
        id(false)
      """,
      "true"
    )
  }

  test("throws error if generic function is not compatible with interface") {
    expectTypeError(
      """
        interface IntIdentity {
          def call(value: Int): Int
        }

        val id: IntIdentity = (value: val T): Boolean { true }
      """
    )
  }

  ignore("can use defined variables in function body") {
    expectCompileTime(
      """
        val typeOf = (any: val T) T

        typeOf(42)
      """,
      "Int"
    )
  }
}
