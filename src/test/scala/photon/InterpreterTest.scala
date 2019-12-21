package photon

import org.scalatest._
import org.scalatest.Matchers._

class InterpreterTest extends FunSuite {
  def parseAsBlock(code: String, macroHandler: Parser.MacroHandler = Parser.BlankMacroHandler): Value = {
    val parser = new Parser(new Lexer("<testing>", code), macroHandler)
    val values = parser.parseAll();

    Value.Operation(Operation.Block(values), None)
  }

  def eval(code: String): Value = {
    val interpreter = new Interpreter()
    val value = parseAsBlock(code, interpreter.macroHandler)

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

  test("can call lambdas") {
    expect("{ 42 }()", "42")
    expect("{ 42 }.call", "42")
    expect("{ |a| a + 41 }(1)", "42")
    expect("{ |a| a + 41 }.call 1", "42")
  }

  test("can partially evaluate code") {
    expect("{ |a| 1 + 41 + a }", "{ |a| 42 + a }")
    expect("{ |a| a + (1 + 41) }", "{ |a| a + 42 }")
  }

  test("assignment") {
    expect("answer = 42; answer", "42")
  }

  test("closures") {
    expect("{ |a| { |b| a + b } }(1)(41)", "42")
  }

  test("higher-order functions") {
    expect("{ |fn| fn(1) }({ |a| a + 41 })", "42")
    expect("{ |fn| fn(1) }({ |a| b = a; b + 41 })", "42")
  }
}
