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
        val ageable = WithAge.of(person)
        
        ageable.age
      """.trimIndent(),
      42
    )
  }

  @Test
  fun testCanAssignToInterfaceThroughTypeAsserts() {
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
}