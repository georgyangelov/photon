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
}