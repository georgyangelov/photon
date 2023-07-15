package photon.lib

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.assertThrows
import photon.compiler.PhotonLanguage
import photon.core.PhotonError
import kotlin.test.assertContains
import kotlin.test.assertEquals

fun expect(code: String, expected: Int) {
  expect(Int::class.java, code, expected)
}

fun expect(code: String, expected: Boolean) {
  expect(Boolean::class.java, code, expected)
}

fun expect(code: String, expected: String) {
  expect(String::class.java, code, expected)
}

fun <T>expect(cls: Class<T>, code: String, expected: T) {
  val context = Context.newBuilder(PhotonLanguage.id).build()
  val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()
  val result = context.eval(source)
  val resultOfType = result.`as`(cls)

  assertEquals(resultOfType, expected)
}

fun expectError(code: String, errorMessageSubstring: String) {
  val context = Context.newBuilder(PhotonLanguage.id).build()
  val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()

  val error = assertThrows<PolyglotException> { context.eval(source) }

  assertContains(error.message ?: "<no error message>", errorMessageSubstring)
}

fun eval(code: String): org.graalvm.polyglot.Value {
  return eval<org.graalvm.polyglot.Value>(code)
}

inline fun <reified T>eval(code: String): T {
  val context = Context.newBuilder(PhotonLanguage.id).allowAllAccess(true).build()
  val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()

  return context.eval(source).`as`(T::class.java)
}