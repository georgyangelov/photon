package photon

import photon.lib.*
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
    expect("val plusOne = (a: Int) a + 1; plusOne(41)", 42)
  }

  @Test
  fun testCanReassignGlobalsForFunctionTypes() {
    expect("val myInt = Int; val plusOne = (a: myInt) a + 1; plusOne(41)", 42)
  }

  @Test
  fun testClosures() {
    expect("val one = 1; val plusOne = (a: Int) a + one; plusOne(41)", 42)
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
        recursive val Person = Class.new "Person", (self: ClassBuilder): Int {
          define "age", Int
          define "answer", (self: Person) age + 1
          
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
        
        val addInt = addOfType(Int)
        
        addInt(41, 1)
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
          define "answer", (self: self.selfType) age + 1
          
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
          def answer() age + 1
        }
        
        val person = Person.new(41)
        person.answer
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testSelfReferencingFunctions() {
    expect(
      """
        class Person {
          def age: Int
          def ageOfOther(other: Person) other.age 
        }
        
        val a = Person.new(42)
        val b = Person.new(1)
        
        b.ageOfOther(a)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testMutuallyRecursiveClassDefinitions() {
    expect(
      """
        class A {
          def age: Int
          def fn(b: B) b.age
        }
        
        class B {
          def age: Int
          def fn(a: A) a.age
        }
        
        val a = A.new(42)
        val b = B.new(1)
        
        b.fn(a)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testNameShadowing() {
    expect(
      """
        val answer = 3
        val answer = 7
        val answer = 42
        
        answer
      """.trimIndent(),
      42
    )

    expect(
      """
        val answer = 3
        val answer = 7
        val fn = () answer
        
        val answer = 42
        
        fn()
      """.trimIndent(),
      7
    )
  }
}
