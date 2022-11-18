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

//  TODO: Actually this shouldn't be possible because of the second case - can't run the function `Optional(T)` with
//  ignore("can infer return type of generic functions") {
//    expectCompileTime(
//      """
//        val identity = (value: val T) value
//
//        identity(true)
//      """,
//      "true"
//    )
//
//    expectCompileTime(
//      """
//        val identity = (value: val T) Optional(T).of(value)
//
//        identity(true)
//      """,
//      "true"
//    )
//
//    expectCompileTime(
//      """
//        val identity = (value: val T) value
//
//        identity(42)
//      """,
//      "42"
//    )
//  }

  // TODO: Move to another file testing pattern matching on values
  ignore("can define match functions on classes") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int

          def static of$match(value: Person) {
            MatchResult.of(name = name, age = age)
          }
        }

        Person.of(name = val name, age = val age) = Person.new(name = "Ivan", age = 42)

        age
      """,
      "42"
    )
  }

  ignore("can define match functions as variables") {}

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
