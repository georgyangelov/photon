package photon.frontend

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.Assertions.*
import photon.compiler.PhotonLanguage
import kotlin.test.*

internal class SimpleTest {
  @Test
  fun testCompilesLiterals() {
    expect("42", 42)
    expect("\"answer\"", "answer")
    expect("true", true)
    expect("false", false)
  }

  @Test()
  fun testCompilesMethodCalls() {
    expect("41 + 1", 42)
  }

  private fun expect(code: String, expected: Int): Unit {
    expect(Int::class.java, code, expected)
  }

  private fun expect(code: String, expected: Boolean): Unit {
    expect(Boolean::class.java, code, expected)
  }

  private fun expect(code: String, expected: String): Unit {
    expect(String::class.java, code, expected)
  }

  fun <T>expect(cls: Class<T>, code: String, expected: T): Unit {
    val context = Context.newBuilder(PhotonLanguage.id).build()
    val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()
    val result = context.eval(source)
    val resultOfType = result.`as`(cls)

    assert(resultOfType == expected)
  }
}
