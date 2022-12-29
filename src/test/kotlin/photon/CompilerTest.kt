package photon

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import photon.compiler.PhotonLanguage

import kotlin.test.*

internal class CompilerTest {
  @Test
  fun testCompilesLiterals() {
    expect("42", 42)
    expect("\"answer\"", "answer")
    expect("true", true)
    expect("false", false)
  }

  @Test
  fun testCompilesMethodCalls() {
    expect("41 + 1", 42)
  }

  @Test
  fun testAssignment() {
    expect("val answer = 42; answer", 42)
    expect("val answer = 42; val another = 11; answer", 42)
  }

  @Test
  fun testFunctions() {
    expect("val plusOne = (a: Int) a + 1; plusOne.call(41)", 42)
  }

  @Test
  fun testCanReassignGlobalsForFunctionTypes() {
    expect("val myInt = Int; val plusOne = (a: myInt) a + 1; plusOne.call(41)", 42)
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

    assertEquals(resultOfType, expected)
  }
}
