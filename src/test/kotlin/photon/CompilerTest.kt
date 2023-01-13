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

  @Test
  fun testClosures() {
    expect("val one = 1; val plusOne = (a: Int) a + one; plusOne.call(41)", 42)
  }

  @Test
  fun testNestedClosures() {
    expect("(a:Int){ (b:Int){ a + b } }(1)(41)", 42)
  }

  @Test
  fun testDefiningClassProperties() {
    expect(
      """
        val Person = Class.new "Person", (self: ClassBuilder): Int {
          define "age", Int
          
          1
        }

        val ivan = Person.new(42)
        ivan.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testDefiningClassMethods() {
    expect(
      """
        val Person = Class.new "Person", (self: ClassBuilder): Int {
          define "answer", () 42
          
          1
        }

        val ivan = Person.new()
        ivan.answer
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testClassMethodsWithSelf() {
    expect(
      """
        val Person = Class.new "Person", (self: ClassBuilder): Int {
          define "age", Int
          define "answer", (self: Person) self.age + 1
          
          1
        }

        val ivan = Person.new(41)
        ivan.answer
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testFunctionsWithPartialArguments() {
    // TODO: If possible make this `@` be a method call on the function itself
    expect(
      """
        val addOfType = @(T: Type) {
          (a: T, b: T): T { a + b } 
        }
        
        val addInt = addOfType.call(Int)
        
        addInt.call(41, 1)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testPartialArguments() {
    expect(
      """
        val builder = @(self: ClassBuilder): Int {
          define "age", Int
          define "answer", (self: self.selfType) self.age + 1
          
          1
        }
        
        val Person = Class.new "Person", builder
        val Person2 = Class.new "Person2", builder

        val ivan = Person.new(41)
        ivan.answer
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testClassMacro() {
    expect(
      """
        class Person {
          def age: Int
          def answer() self.age + 1
        }
        
        val person = Person.new(41)
        person.answer
      """.trimIndent(),
      42
    )
  }

  private fun expect(code: String, expected: Int) {
    expect(Int::class.java, code, expected)
  }

  private fun expect(code: String, expected: Boolean) {
    expect(Boolean::class.java, code, expected)
  }

  private fun expect(code: String, expected: String) {
    expect(String::class.java, code, expected)
  }

  fun <T>expect(cls: Class<T>, code: String, expected: T) {
    val context = Context.newBuilder(PhotonLanguage.id).build()
    val source = Source.newBuilder(PhotonLanguage.id, code, "test.y").build()
    val result = context.eval(source)
    val resultOfType = result.`as`(cls)

    assertEquals(resultOfType, expected)
  }
}
