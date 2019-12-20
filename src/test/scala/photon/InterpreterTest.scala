package photon

import org.scalatest.FunSuite

class InterpreterTest extends FunSuite {
  def parseAsBlock(code: String): Value = {
    val parser = new Parser(new Lexer("<testing>", code), Parser.BlankMacroHandler)
    val values = parser.parseAll();

    Value.Operation(Operation.Block(values), None)
  }

  def eval(code: String): Value = {
    val value = parseAsBlock(code)
    val interpreter = new Interpreter()

    interpreter.evaluate(value)
  }

  def expect(actualCode: String, expectedCode: String): Unit = {
    assert(s"{ ${eval(actualCode).inspect} }" == parseAsBlock(expectedCode).inspect)
  }

  test("can eval simple values") {
    expect("42", "42")
  }

  test("can call native methods") {
    expect("1 + 41", "42")
    expect("1 + 40 + 1 + 1 - 1", "42")
  }
}
