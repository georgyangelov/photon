package photon

import org.scalatest.FunSuite
import photon.TestHelpers._

class ClassTest extends FunSuite {
  test("can create classes with fields") {
    expectEvalCompileTime(
      """
      Person = Class.new(
        fields = List.of(
          Class.field("name", String),
          Class.field("age", Int)
        ),

        instanceMethods = List.empty
      )

      person = Person.new(name = "Ivan", age = 42)

      person.age
      """,
      "42"
    )
  }

  test("can create classes with methods using self") {
    expectEvalCompileTime(
      """
      Person = Class.new(
        fields = List.of(
          Class.field("name", String),
          Class.field("age", Int)
        ),

        instanceMethods = List.of(
          Class.method("nextAge", (): Int { age + 1 })
        )
      )

      person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }
}
