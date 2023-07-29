package photon

import org.junit.Test
import photon.lib.eval
import java.util.function.Function
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

interface WithAge {
  fun age(): Int
}

class Person(val name: String, val age: java.lang.Integer) {
  fun agePlus(num: java.lang.Integer) = age.toInt() + num.toInt()
}

internal class InteropTest {
  @Test
  fun testIntLiterals() {
    val result = eval("42")

    assertTrue(result.isNumber)
    assertEquals(42, result.asInt())
  }

  @Test
  fun testMethodCalls() {
    val result = eval(
      """
        object {
          def age() 42
        }
      """.trimIndent()
    )

    assertTrue(result.canInvokeMember("age"))
    assertEquals(42, result.invokeMember("age").asInt())
  }

  @Test
  fun testObjectCastToInterface() {
    val result = eval<WithAge>(
      """
        object {
          def age() 42
        }
      """.trimIndent()
    )

    assertEquals(42, result.age())
  }

  @Test
  fun testCallingPhotonFunctions() {
    val result = eval<Supplier<Int>>(
      """
        () 42
      """.trimIndent()
    )

    assertEquals(42, result.get())
  }

  @Test
  fun testCallingPhotonFunctionsWithArguments() {
    val result = eval<Function<Int, Int>>(
      """
        (a: Int) a + 1
      """.trimIndent()
    )

    assertEquals(42, result.apply(41))
  }

  @Test
  fun testCallingJavaMethodsInPhoton() {
    val result = eval<Function<WithAge, Int>>(
      """
        interface Person {
          def age(): Int
        }

        (arg: Person) { arg.age + 1 }
      """.trimIndent()
    )

    val person = object: WithAge {
      override fun age() = 41
    }

    assertEquals(42, result.apply(person))
  }

  @Test
  fun testCallingPhotonObjectsWithCallMethodFromJava() {
    val result = eval<Supplier<Int>>(
      """
        object {
          def call() 42
        }
      """.trimIndent()
    )

    assertEquals(42, result.get())
  }

  @Test
  fun testJavaInterop() {
    val result = eval<Int>(
      """
        Interop.answer
      """.trimIndent()
    )

    assertEquals(42, result)
  }

  @Test
  fun testLoadingJavaClassesDirectly() {
    val result = eval<Int>(
      """
        interface Person {
          def name: String
          def age: Int
          def agePlus(num: Int): Int
        }

        val PersonClass = Interop.loadClass("photon.Person")

        val person: Person = PersonClass.getConstructor(
        	Interop.loadClass("java.lang.String"),
          Interop.loadClass("java.lang.Integer")
        ).newInstance("Ivan", 42)
        
        person.agePlus(1)
      """.trimIndent()
    )

    assertEquals(43, result)
  }
}