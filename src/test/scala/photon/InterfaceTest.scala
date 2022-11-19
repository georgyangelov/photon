package photon

import org.scalatest._
import photon.support.TestHelpers._

class InterfaceTest extends FunSuite {
  ignore("can use the Interface#from method") {
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

  test("can assign fields to interfaces") {
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

  ignore("can convert to interfaces for arguments") {
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

  test("can convert to interfaces for arguments automatically") {
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

        ageOf Person.new(age = 42)
      """,
      "43"
    )
  }

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
          def add(other: Int): Int
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
          def add(other: Int): Int

          def plusOne { add 1 }
        }

        class NaturalNumber {
          def value: Int

          def add(other: Int) value + other
        }

        val number: Number = NaturalNumber.new(value = 42)
        number.plusOne
      """,
      "43"
    )
  }

  test("can assign functions to interfaces") {
    expectCompileTime(
      """
        interface Producer {
          def call: Int
        }

        val producer: Producer = () 42
        producer.call
      """,
      "42"
    )
  }

  test("can assign functions with arguments to interfaces") {
    expectCompileTime(
      """
        interface Producer {
          def call(a: Int): Int
        }

        val producer: Producer = (a: Int) 42 + a
        producer.call(1)
      """,
      "43"
    )
  }

  test("can assign functions with arguments to function interfaces") {
    expectCompileTime(
      """
        val Producer = (a: Int): Int

        val producer: Producer = (a: Int) 42 + a
        producer.call(1)
      """,
      "43"
    )
  }

  test("function type definitions can self-reference") {
    expectCompileTime(
      """
        val Next = (a: Int, b: Next): Int

        val producer: Next = (a: Int, b: Next) 42 + a
        producer.call(1, producer)
      """,
      "43"
    )
  }

  test("can create anonymous interfaces") {
    expectCompileTime(
      """
        val Aged = interface {
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

  test("cannot define interfaces with generic methods") {
    expectParseError(
      """
        interface WithIdentity {
          def identity(value: val T): T
        }
      """
    )
  }

  test("cannot define function types with generic types") {
    expectParseError(
      """
        val GenericFn = (value: val T): T
      """
    )

    expectParseError(
      """
        val fn: (value: val T): T = (value: val T): T { value }
      """
    )
  }

  ignore("can define static methods") {
    expectCompileTime(
      """
        interface Universe {
          def static answer() 42
        }

        Universe.answer
      """,
      "42"
    )
  }
}
