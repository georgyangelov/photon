package photon

import photon.lib.*
import kotlin.test.*

internal class InterfaceTest {
  @Test
  fun testSimpleInterfaceDefinitions() {
    expect(
      """
        class Person {
          def age: Int
        }
        
        interface WithAge {
          def age: Int
        }
        
        val person = Person.new(42)
        val ageable: WithAge = person
        
        ageable.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testInterfacesWithMethods() {
    expect(
      """
        class Person {
          def age: Int
        }
        
        interface WithAge {
          def age(): Int
        }
        
        val person = Person.new(42)
        val ageable: WithAge = person
        
        ageable.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testInterfacesWithMethodsWithArguments() {
    expect(
      """
        class Person {
          def age: Int
          def agePlus(other: Int): Int { self.age + other }
        }
        
        interface WithAge {
          def agePlus(other: Int): Int
        }

        val person = Person.new(41)
        val ageable: WithAge = person
        
        ageable.agePlus(1)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssigningLambdasToFunctionTypes() {
    expect(
      """
        val add = (a: Int, b: Int) a + b
        val addFn: (a: Int, b: Int): Int = add
        
        addFn.call(41, 1)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testAssigningCallablesToFunctionTypes() {
    expect(
      """
        class Something {
          def addedValue: Int
          def call(a: Int, b: Int) { a + b + self.addedValue }
        }
        
        val addFn: (a: Int, b: Int): Int = Something.new(1)
        
        addFn.call(41, 1)
      """.trimIndent(),
      43
    )
  }

  @Test
  fun testStoringFunctionsAsValues() {
    expect(
      """
        class Something {
          def fn: (a: Int): Int
        }
        
        val fn: (a: Int): Int = (a: Int) a + 1
        val something = Something.new(fn)
        
        something.fn.call(41)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testStoringFunctionsAsValuesByArgumentTypeConversions() {
    expect(
      """
        class Something {
          def fn: (a: Int): Int
        }
        
        val something = Something.new((a: Int) a + 1)
        
        something.fn.call(41)
      """.trimIndent(),
      42
    )
  }

//  TODO: Handle the StackOverflow here
//  @Test
//  fun testConvertingParametersBetweenFunctionTypes() {
//    expect(
//      """
//        class Person {
//          def age: Int
//          def ageOf(person: Person) person.age
//        }
//
//        interface Ageable {
//          def age: Int
//          def ageOf(ageable: Ageable): Int
//        }
//
//        val person = Person.new(42)
//        val ageable: Ageable = person
//
//        # ageable.ageOf(person)
//        # ageable.ageOf(ageable)
//        42
//      """.trimIndent(),
//      42
//    )
//  }

  @Test
  fun testConvertingParametersBetweenFunctionTypes() {
    expect(
      """
        class Person {
          def age: Int
          def other: Int
        }
        
        interface Ageable {
          def age: Int
        }
        
        val ageOf = (ageable: Ageable) ageable.age
        val ageOfPerson: (person: Person): Int = ageOf
        
        ageOfPerson.call Person.new(42, 11)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testConvertingReturnTypesBetweenFunctionTypes() {
    expect(
      """
        class Person {
          def age: Int
        }
        
        interface Ageable {
          def age: Int
        }
        
        val newPerson = (age: Int) Person.new(age) 
        val newAgeable: (age: Int): Ageable = newPerson
        
        newAgeable.call(42).age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testConcreteMethodsOnInterfaces() {
    expect(
      """
        class Person {
          def age: Int
        }
        
        interface Ageable {
          def age: Int
          def agePlus(x: Int) age + x
        }
        
        val ageable: Ageable = Person.new(41)
        
        ageable.agePlus(1)
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testStaticMethodsOnInterfaces() {
    expect(
      """
        interface Something {
          static {
            def answer() 42
          }
        }
        
        Something.answer
      """.trimIndent(),
      42
    )
  }
}