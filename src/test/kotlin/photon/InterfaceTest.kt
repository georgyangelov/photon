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
}