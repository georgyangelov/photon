package photon

import org.scalatest.FunSuite
import photon.TestHelpers.{expectEval, expectEvalCompileTime, expectFailCompileTime}

class ObjectTest extends FunSuite {
  test("simple objects") {
    expectEvalCompileTime("Object(a = 42).a", "42")
    expectEvalCompileTime("o = Object(a = 42); o.a", "42")
    expectFailCompileTime("o = Object(a = 42); o.b", "Cannot call method b on Object.call(a = 42)")
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
          call = (_, name, age) {
            Object(name = name, age = age)
          },

          humanAge = (_, dog) dog.age * 7
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

  test("binding to variable names in objects") {
    expectEvalCompileTime("Answer = Object(call = () Object($type = Answer), get = 42); Answer().$type.get", "42")
  }

  test("fails when binding recursively") {
    expectFailCompileTime("Answer = Object(get = Answer); Answer.get", "Cannot use the name Answer during declaration")
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

  test("objects support prototypes") {
    expectEval(
      """
         Cat = Object(meow = () "Meow!")
         kitten = Object($prototype = Cat)

         kitten.meow
      """,
      "'Meow!'"
    )
  }

  test("object functions support passing a self argument") {
    expectEval(
      """
         Cat = Object(meow = (self) self.name)
         kitten = Object($prototype = Cat, name = "Mittens")

         kitten.meow
      """,
      "'Mittens'"
    )
  }
}
