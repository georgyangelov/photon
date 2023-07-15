package photon

import org.junit.Test
import photon.lib.eval
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
}