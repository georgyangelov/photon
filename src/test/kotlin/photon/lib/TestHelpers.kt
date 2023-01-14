package photon.lib

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import photon.compiler.PhotonLanguage
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