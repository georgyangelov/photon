package photon

import org.scalatest.FunSuite
import photon.TestHelpers._

class ClassTest extends FunSuite {
  test("can create classes with fields") {
    expectEvalCompileTime(
      """
      val Person = Class.new(
        Class.property("name", String),
        Class.property("age", Int)
      )

      val person = Person.new(name = "Ivan", age = 42)

      person.age
      """,
      "42"
    )
  }

  test("can create classes with methods using self") {
    expectEvalCompileTime(
      """
      val Person = Class.new(
        Class.property("name", String),
        Class.property("age", Int),

        Class.method("nextAge", (self: Person): Int { age + 1 })
      )

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes with properties referencing the class") {
    expectEvalCompileTime(
      """
      val Person = Class.new(
        Class.property("name", String),
        Class.property("parent", Optional(Person))
      )

      val person = Person.new(name = "Ivan", parent = Optional(Person).empty)
      person.parent
      """,
      "Optional(Person).empty"
    )
  }

//  test("can create mutually-recursive classes") {
//    expectEvalCompileTime(
//      """
//      recursive {
//        OptionalPerson = Optional(Person),
//
//        Person = Class.new(
//          fields = List.of(
//            Class.field("name", String),
//            Class.field("parent", (): Type { OptionalPerson })
//          ),
//          instanceMethods = List.of()
//        )
//      }
//
//      person = Person.new(name = "Ivan", parent = OptionalPerson.none)
//      person.parent
//      """,
//      "None"
//    )
//  }
}
