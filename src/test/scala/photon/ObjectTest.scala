package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEvalCompileTime, expectFailCompileTime}

class ObjectTest extends FunSuite {
  test("simple objects") {
    expectEvalCompileTime("Object(a = 42).a", "42")
    expectEvalCompileTime("o = Object(a = 42); o.a", "42")
    expectFailCompileTime("o = Object(a = 42); o.b", "Cannot call method b on Object(a = 42)")
  }

  test("calling methods on objects") {
    expectEvalCompileTime("Object(a = 42).a", "42")
    expectEvalCompileTime("Object(a = { 42 }).a", "42")
    expectEvalCompileTime("Object(a = { 42 }).a()", "42")
    expectEvalCompileTime("Object(a = { { 42 } }).a.call", "42")
    expectEvalCompileTime("Object(a = { { 42 } }).a()()", "42")
  }

  test("using object constructors") {
    expectEvalCompileTime(
      """
        Dog = Object(
          call = (name, age) {
            Struct(name = name, age = age)
          },

          humanAge = (self) self.age * 7
        )

        ralph = Dog("Ralph", age = 2)
        humanAge = Dog.humanAge(ralph)

        humanAge
      """,
      "14"
    )
  }

  test("supports objects") {
    expectEvalCompileTime(
      "user = Object(name = 'Joro'); user.name",
      "'Joro'"
    )
    expectEvalCompileTime(
      "computer = Object(answer = 1 + 41); computer.answer",
      "42"
    )
  }

  test("compile-time evaluation of partial objects") {
    expectEvalCompileTime(
      "unknown = () { 11 }.runTimeOnly; object = Object(unknown = unknown, answer = () 42); object.answer",
      "42"
    )
    expectEvalCompileTime(
      "unknown = () { 42 }.runTimeOnly; object = Object(unknown = unknown, answer = () 42); object.unknown",
      "unknown = () { 42 }; object = Object(unknown = unknown, answer = () 42); object.unknown"
    )
  }

  test("binding to variable names in objects") {
    expectEvalCompileTime("Answer = Object(call = () Object($type = Answer), get = 42); Answer().$type.get", "42")
    expectFailCompileTime("Answer = Object(get = Answer); Answer.get", "Recursive reference to value being declared")
  }

  test("compile-time evaluation of objects") {
    expectEvalCompileTime(
      "object = Object(answer = 42); object.answer",
      "42"
    )
    expectEvalCompileTime(
      "object = Object(answer = () 42); object.answer",
      "42"
    )
    expectEvalCompileTime(
      "object = Object(call = () 42); object()",
      "42"
    )
  }

  test("variables do not escape their scope as operations on partial structs") {
    expectEvalCompileTime(
      "{ unknown = () { 42 }.runTimeOnly; Object(method = unknown) }().method()",
      "(unknown = () { 42 }; Object(method = unknown)).method()"
    )
  }
}
