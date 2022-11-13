package photon

import org.scalatest._
import photon.support.TestHelpers._

class ClassTest extends FunSuite {
  test("can create classes with values") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "name", String
        define "age", Int
      }

      val person = Person.new(name = "Ivan", age = 42)

      person.age
      """,
      "42"
    )
  }

  test("supports class syntax for values") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int
        }

        val person = Person.new(name = "Ivan", age = 42)

        person.age
      """,
      "42"
    )
  }

  test("supports class syntax for methods") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int

          def nextAge: Int { age + 1 }
          def agePlus(x: Int) { age + 1 }
        }

        val person = Person.new(name = "Ivan", age = 42)

        person.nextAge
      """,
      "42"
    )
  }

  ignore("supports forward-referencing methods") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int

          def nextAge: Int { agePlus 1 }
          def agePlus(x: Int) { age + x }
        }

        val person = Person.new(name = "Ivan", age = 42)

        person.nextAge
      """,
      "42"
    )
  }

  ignore("can call functions with arguments") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int

          def nextAge: Int { age + 1 }
          def agePlus(x: Int) { age + x }
        }

        val person = Person.new(name = "Ivan", age = 42)

        person.agePlus 4
      """,
      "46"
    )
  }

  ignore("can call functions with named arguments") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def age: Int

          def nextAge: Int { age + 1 }
          def agePlus(x: Int) { age + x }
        }

        val person = Person.new(name = "Ivan", age = 42)

        person.agePlus x = 4
      """,
      "46"
    )
  }

  test("can create classes with methods using self") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "name", String
        define "age", Int

        define "nextAge", (self: Person): Int { age + 1 }
      }

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes when we don't have a direct reference to the name (inline)") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "name", String
        define "age", Int

        define "nextAge", (self: classType) { age + 1 }
      }

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes when we don't have a direct reference to the name") {
    expectCompileTime(
      """
      val classBuildFn = (self: ClassBuilder) {
        define "name", String
        define "age", Int

        define "nextAge", (self: classType) { age + 1 }
      }

      val Person = Class.new("Person", classBuildFn)

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes with methods using return type inference") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "name", String
        define "age", Int

        define "nextAge", (self: Person) age + 1
      }

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes with properties referencing the class") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "name", String
        define "parent", Optional(Person)
      }

      val person = Person.new(name = "Ivan", parent = Optional(Person).empty)
      person.parent
      """,
      "Optional(Person).empty"
    )
  }

//  test("can create mutually-recursive classes") {
//    expectCompileTime(
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
