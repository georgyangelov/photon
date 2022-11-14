package photon

import org.scalatest._
import photon.support.TestHelpers._

class InterfaceTest extends FunSuite {
  test("can assign fields to interfaces") {
    expectCompileTime(
      """
        interface Aged {
          def age: Int
        }

        class Person {
          def age: Int
        }

        val aged = Aged.from(Person.new(age = 42))
        aged.age
      """,
      "42"
    )
  }

  test("can use type assertions to create interfaces") {
    expectCompileTime(
      """
        interface Aged {
          def age: Int
        }

        class Person {
          def age: Int
        }

        val aged: Aged = Person.new(age = 42)
        aged.age
      """,
      "42"
    )
  }

  test("can convert to interfaces for arguments") {
    expectCompileTime(
      """
        interface Aged {
          def age: Int
          def nextAge { age + 1 }
        }

        class Person {
          def age: Int
        }

        val ageOf = (aged: Aged) aged.nextAge

        ageOf Aged.from(Person.new(age = 42))
      """,
      "43"
    )
  }

// TODO: Implement this later when we have enough of the type checking
//
//  test("can convert to interfaces for arguments automatically") {
//    expectCompileTime(
//      """
//        interface Aged {
//          def age: Int
//          def nextAge { age + 1 }
//        }
//
//        class Person {
//          def age: Int
//        }
//
//        val ageOf = (aged: Aged) aged.nextAge
//
//        ageOf Aged.from(Person.new(age = 42))
//      """,
//      "43"
//    )
//  }

  test("can assign methods to interfaces") {
    expectCompileTime(
      """
        interface Aged {
          def age: Int
        }

        class Person {
          def age { 42 }
        }

        val aged: Aged = Person.new
        aged.age
      """,
      "42"
    )
  }

  test("preserves self of methods") {
    expectCompileTime(
      """
        interface Aged {
          def nextAge: Int
        }

        class Person {
          def age: Int

          def nextAge { age + 1 }
        }

        val aged: Aged = Person.new(age = 42)
        aged.nextAge
      """,
      "43"
    )
  }

  test("can assign methods with arguments") {
    expectCompileTime(
      """
        interface Number {
          def add(x: Int): Int
        }

        class NaturalNumber {
          def value: Int

          def add(other: Int) { value + other }
        }

        val number: Number = NaturalNumber.new(value = 42)
        number.add(1)
      """,
      "43"
    )
  }

  test("can define actual methods on interfaces") {
    expectCompileTime(
      """
        interface Number {
          def add(x: Int): Int

          def plusOne { add 1 }
        }

        class NaturalNumber {
          def value: Int

          def add(other: Int) { value + other }
        }

        val number: Number = NaturalNumber.new(value = 42)
        number.plusOne
      """,
      "43"
    )
  }
}
