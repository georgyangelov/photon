package photon.frontend

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

internal class ParserTest {
  @Test
  fun testNumberLiterals() {
    assertEquals(parse("12345 "), "12345")
  }

  private fun parse(code: String): String {
    return Parser(Lexer("<testing>", code), Parser.BlankMacroHandler)
      .parseAll()
      .joinToString(" ") { it.inspect() }
  }
}