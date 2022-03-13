package photon
import org.scalatest.FunSuite
import photon.TestHelpers._

class InterfaceTest extends FunSuite {
  ignore("can create interfaces with methods") {
    expectEvalCompileTime(
      """
      class Person {
        def name: String
        def age: Int
      }

      interface Aged {
        def age: Int

        def nextAge: Int { age + 1 }
      }

      val person = Person.new(name = "Ivan", age = 42)
      val aged: Aged = person

      person.age
      """,
      "42"
    )
  }

  ignore("can convert between interfaces for function arguments") {
    expectEvalCompileTime(
      """
        class Person {
          def name: String
          def age: Int
        }

        interface WithAge { def age: Int }

        fn = (aged: WithAge) aged.age + 1

        fn(Person.new(name = "Ivan", age = 42))
      """,
      "43"
    )
  }
}
