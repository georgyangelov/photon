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

        methods = List.empty
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

  test("can create classes with properties referencing the class") {
    expectEvalCompileTime(
      """
      Person = Class.new(
        fields = List.of(
          Class.field("name", String),
          Class.field("parent", lazy Optional(Person))
        ),
        instanceMethods = List.of()
      )

      person = Person.new(name = "Ivan", parent = Optional(Person).none)
      person.parent
      """,
      "None"
    )
  }

  test("can create mutually-recursive classes") {
    expectEvalCompileTime(
      """
      recursive {
        OptionalPerson = Optional(Person),

        Person = Class.new(
          fields = List.of(
            Class.field("name", String),
            Class.field("parent", (): Type { OptionalPerson })
          ),
          instanceMethods = List.of()
        )
      }

      person = Person.new(name = "Ivan", parent = OptionalPerson.none)
      person.parent
      """,
      "None"
    )
  }
}
