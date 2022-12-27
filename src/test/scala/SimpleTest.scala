import org.graalvm.polyglot.{Context, Source}
import photon.PhotonLanguage
import org.scalatest.funsuite._

import scala.reflect.ClassTag

class SimpleTest extends AnyFunSuite {
  test("compiles literals") {
    expect("42", 42)
    expect("\"answer\"", "answer")
    expect("true", true)
    expect("false", false)
  }

  test("compiles method calls") {
    expect("41 + 1", 42)
  }

  def expect(code: String, expected: Integer): Unit = {
    expect[Integer](code, expected)
  }

  def expect(code: String, expected: java.lang.Boolean): Unit = {
    expect[java.lang.Boolean](code, expected)
  }

  def expect[T <: AnyRef](code: String, expected: T)(implicit tag: ClassTag[T]): Unit = {
    val context = Context.newBuilder(PhotonLanguage.id).build()
    val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()
    val result = context.eval(source)
    val resultOfType = result.as(tag.runtimeClass)

    assert(resultOfType == expected)
  }
}
