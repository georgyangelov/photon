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

  test("can manually define methods") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "age", Int

        define "nextAge", (self: selfType) { age + 1 }
      }

      val person = Person.new(age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can manually define methods without self") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "answer", { 42 }
      }

      Person.new.answer
      """,
      "42"
    )
  }

  test("can manually define methods referencing self through the variable name") {
    expectCompileTime(
      """
      val Person = Class.new "Person", (self: ClassBuilder) {
        define "age", Int

        define "nextAge", (self: Person) { age + 1 }
      }

      val person = Person.new(age = 42)

      person.nextAge
      """,
      "43"
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
      "43"
    )
  }

  test("supports forward-referencing methods") {
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
      "43"
    )
  }

  test("supports using classes as argument type") {
    expectCompileTime(
      """
        class Other {
          def answer { 42 }
        }

        class Person {
          def giveAnswer(other: Other) { other.answer }
        }

        val person = Person.new

        person.giveAnswer(Other.new)
      """,
      "42"
    )
  }

  test("supports using the same class as argument type") {
    expectCompileTime(
      """
        class Person {
          def age: Int

          def sumOfAges(other: Person) { age + other.age }
        }

        val a = Person.new(age = 20)
        val b = Person.new(age = 22)

        a.sumOfAges(b)
      """,
      "42"
    )
  }

  test("supports defining explicit self argument on methods") {
    expectCompileTime(
      """
        class Other {
          def answer { 42 }
        }

        class Person {
          def giveAnswer(self: Other) { answer }
        }

        val person = Person.new

        person.giveAnswer(Other.new)
      """,
      "42"
    )
  }

  test("can call functions with arguments") {
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

  test("can call functions with named arguments") {
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

        define "nextAge", (self: selfType) { age + 1 }
      }

      val person = Person.new(name = "Ivan", age = 42)

      person.nextAge
      """,
      "43"
    )
  }

  test("can create classes when we don't have a direct reference to the name") {
    // TODO: Create a test that it throws an error if this function is not `compileTimeOnly`, or make it work
    expectCompileTime(
      """
      val classBuildFn = (self: ClassBuilder) {
        define "name", String
        define "age", Int

        define "nextAge", (self: selfType) { age + 1 }
      }.compileTimeOnly

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

  // TODO: Need Optional for this
  ignore("can create classes with properties referencing the class") {
    expectCompileTime(
      """
        class Person {
          def name: String
          def parent: Optional(Person)
        }

        val person = Person.new(name = "Ivan", parent = Optional(Person).empty)
        person.parent
      """,
      "Optional(Person).empty"
    )
  }

  test("can create anonymous classes") {
    expectCompileTime(
      """
      val Person = class {
        def age: Int
      }

      val person = Person.new(age = 42)

      person.age
      """,
      "42"
    )
  }

  test("can define singleton objects without macro") {
    expectCompileTime(
      """
        val Something = Object.new "Something", (self: ClassBuilder) {
          def answer() 42
        }

        Something.answer
      """,
      "42"
    )
  }

  test("can define singleton objects") {
    expectCompileTime(
      """
        object Something {
          def answer() 42
        }

        Something.answer
      """,
      "42"
    )
  }

  test("can create generic classes manually") {
    expectCompileTime(
      """
        object Something {
          def call(T: Type) Internal.cacheType Something, T, () class {
            def identity(a: T): T { a }
          }
        }

        val something = Something(Int).new
        something.identity(42)
      """,
      "42"
    )
  }

  ignore("can create generic classes") {
    expectCompileTime(
      """
        class Something(T: Type) {
          def identity(a: T): T { a }
        }

        val something = Something(Int).new
        something.identity(42)
      """,
      "42"
    )
  }

  ignore("can define static methods") {
    expectCompileTime(
      """
        class Person {
          static {
            def ivan() Person.new(name = "Ivan", age = 42)
          }
        }

        Person.ivan.age
      """,
      "42"
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
