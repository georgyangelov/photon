package photon

import org.scalatest._
import org.scalatest.Matchers._
import photon.transforms.AssignmentTransform

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

  test("assignment transform") {
    val block = parseAsBlock("inc = { |a| a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?")

    AssignmentTransform.transform(block).inspect should equal("{ (call (lambda [(param inc)] { (call (lambda [(param a)] { (call (lambda [(param b)] { (+ (+ (inc self a) (inc self b)) $?) }) (+ a 1)) }) 3) }) (lambda [(param a)] { (+ a 1) })) }")
  }

  test("partial evaluation with unknowns") {
    expect("{ |a| a + 1 + $? }(3)", "4 + $?")
    expect("a = 3; b = a + 1; a + b + $?", "7 + $?")
    expect("{ |fn| fn(1) + fn(2) + $? }({ |a| a + 1 })", "5 + $?")
    expect("inc = { |a| a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?", "9 + $?")
    expect("fn = { |a| a + 1 }; fn(1) + fn(2) + $?", "5 + $?")
  }

//  test("advanced partial evaluation") {
//    expect("{ |a| { |b| { |c| a + b + c }(2) }($?) }(1)", "{ |b| 1 + b + 2 }($?)")
//    expect("a = 1; b = $?; c = 2; a + b + c", "b = $?; 1 + b + 2")
//  }
}
