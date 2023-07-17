package photon

import org.junit.Test
import photon.lib.eval
import java.util.function.Function
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

  interface WithAge {
    fun age(): Int
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
}