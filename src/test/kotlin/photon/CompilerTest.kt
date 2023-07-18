package photon

import photon.compiler.types.IntType
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
        val Person = Class.new "Person", @(self: ClassBuilder): Int {
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
        val Person = Class.new "Person", @(self: ClassBuilder): Int {
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
        recursive val Person = Class.new "Person", @(self: ClassBuilder): Int {
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

  @Test
  fun testCanCreateSingletonObjects() {
    expect(
      """
        object Math {
          def plusOne(a: Int) a + 1
        }
        
        Math.plusOne 41
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testTemplateMethods() {
    expect(
      """
        val identity = (a: val T): T a
        
        identity(42)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssignmentOfTemplateMethods() {
    expect(
      """
        val identity = (a: val T): T a
        val intIdentity: (a: Int): Int = identity
        
        intIdentity(42)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testTypeInferenceOfTemplateMethods() {
    expect(
      """
        val identity = (a: val T) a
        
        identity(42)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssignmentOfTemplateMethodsToInterfaces() {
    expect(
      """
        class Person {
          def identity(a: val T) a
        }
        
        interface Something {
          def identity(a: Int): Int
        }
        
        val something: Something = Person.new
        
        something.identity(42)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssigningConcreteFunctionsToFunctionTemplateTypes() {
    expect(
      """
        val intIdentity = (a: Int) a
        val applyIdentity = (fn: (a: val T): T, value: T): T fn(value)
        
        applyIdentity intIdentity, 42
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssigningFunctionInterfacesToFunctionTemplateTypes() {
    expect(
      """
        val intIdentity: (a: Int): Int = (a: Int) a
        val applyIdentity = (fn: (a: val T): T, value: T): T fn(value)
        
        applyIdentity intIdentity, 42
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssigningInterfacesToFunctionTemplateTypes() {
    expect(
      """
        interface IntIdentity {
          def call(a: Int) a
        }
        
        val intIdentity: IntIdentity = (a: Int) a
        val applyIdentity = (fn: (a: val T): T, value: T): T fn(value)
        
        applyIdentity intIdentity, 42
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testConvertingParametersOfFunctionTemplateTypes() {
    expect(
      """
        class Person {
          def age: Int
        }
        
        interface Ageable {
          def age: Int
        }
        
        val person = Person.new(41)
        
        val agePlus = (a: Ageable, b: Int) a.age + b
        val agePlusOne = (fn: (a: Person, b: val T): T, person: Person): T fn(person, 1)
        
        agePlusOne agePlus, person
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCompanionObjects() {
    expect(
      """
        class Person {
          self.static @(self: ClassBuilder) {
            def answer() 42
          }

          def age: Int
        }

        val person = Person.new(Person.answer)
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCompanionObjectsWithMacro() {
    expect(
      """
        class Person {
          static {
            def answer() 42
          }

          def age: Int
        }

        val person = Person.new(Person.answer)
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCompanionObjectFactories() {
    expect(
      """
        class Person {
          static {
            def create(age: Int) Person.new(age)
          }

          def age: Int
        }

        val person = Person.create(42)
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCapturesOfCompileTimeOnlyLambdas() {
    expect(
      """
        val value = 42
        val staticFn = @{ { value } }
        val fn = staticFn()

        fn()
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCapturesOfOtherClasses() {
    expect(
      """
        class A {
          static { def answer() 42 }
        }

        class B {
          static { def answer() A.answer }
        }

        B.answer()
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCapturesOfMethods() {
    expect(
      """
        val answer = () 42
        
        class Person {
          def age() answer()
        }

        val person = Person.new
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testMethodsReferencingStaticMethods() {
    expect(
      """
        class Person {
          static {
            def answer() 42
          }
          
          def age() Person.answer
        }
        
        val person = Person.new
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testSelfInCompanionObjects() {
    expect(
      """
        class Person {
          static {
            def create(age: Int) new(age)
          }

          def age: Int
        }

        val person = Person.create(42)
        person.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testRecursiveReferenceToTypeInCompanionObjects() {
    expect(
      """
        class Person {
          static {
            def ageOf(person: Person) person.age
          }

          def age: Int
        }

        val person = Person.new(42)
        Person.ageOf(person)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testThrowsErrorWhenPartialOnlyFunctionCapturesDynamicValues() {
    expectError(
      """
        val fnA = () 42
        val answer = fnA()
        
        val capture = @() answer
        capture()
      """.trimIndent(),
      "Partial-only function captures dynamic value"
    )
  }

  @Test
  fun testDoesNotThrowAnErrorWhenPartialOnlyFunctionReferencesItself() {
    expect(
      """
        recursive val fn = @() fn()
        
        42
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testDoesNotThrowAnErrorForMutuallyRecursivePartialFunctions() {
    expect(
      """
        val fnA = @() fnB()
        recursive val fnB = @() fnA()

        42
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAnyStaticForPrimitives() {
    expect(
      """
        val fn = @() { 42 }
        
        fn()
      """.trimIndent(),
      42
    )
  }

//  @Test
//  fun testPartialFunctionCanCaptureObjectInstances() {
//    expect(
//      """
//        class Person {
//          def age: Int
//        }
//        interface WithAge { def age(): Int }
//
//        val concreteInterfaceInstance: WithAge = Person.new(42)
//        val fn = @() concreteInterfaceInstance
//        val value = fn()
//
//        value.age()
//      """.trimIndent(),
//      42
//    )
//  }

//  @Test
//  fun testPartialFunctionCanCaptureObjectInstancesThroughObjectLiteral() {
//    expect(
//      """
//        interface WithAge { def age(): Int }
//
//        val concreteInterfaceInstance: WithAge = object { def age() 42 }
//        val fn = @() concreteInterfaceInstance
//        val value = fn()
//
//        value.age()
//      """.trimIndent(),
//      42
//    )
//  }

  @Test
  fun testAnyStaticForInterfaceInstances() {
    expect(
      """
        interface WithAge { def age(): Int } 
        
        val fn = @() {
          val concreteInterfaceInstance: WithAge = object { def age() 42 }
          concreteInterfaceInstance
        }
        
        val value: WithAge = fn()
        
        value.age()
      """.trimIndent(),
      42
    )
  }

//  @Test
//  fun testAnyStaticForTypeObjects() {
//    expect(
//      """
//        val fn = @() { Int }
//
//        fn()
//      """.trimIndent(),
//      IntType
//    )
//  }

//  @Test
//  fun testMatchInVals() {
//    expect(
//      """
//        class Person {
//          def age: Int
//        }
//
//        val ageOfPerson = (person: Person) List.new(person.age)
//
//        ageOfPerson(val age) = Person.new(42)
//
//        age
//      """.trimIndent(),
//      42
//    )
//  }
}
