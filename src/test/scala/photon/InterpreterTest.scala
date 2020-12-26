package photon

import org.scalatest._
import org.scalatest.Matchers._
import photon.transforms.AssignmentTransform

class InterpreterTest extends FunSuite {
  def parseAsBlock(code: String, macroHandler: Parser.MacroHandler = Parser.BlankMacroHandler): Value = {
    val parser = new Parser(new Lexer("<testing>", code), macroHandler)
    val values = parser.parseAll()

    Value.Operation(Operation.Block(values), None)
  }

  def eval(code: String): Value = {
    val interpreter = new Interpreter()
    val value = parseAsBlock(code, interpreter.macroHandler)

    interpreter.evaluate(value)
  }

  def eval(prelude: String, code: String): Value = {
    val interpreter = new Interpreter()
    val preludeValue = parseAsBlock(prelude, interpreter.macroHandler)
    interpreter.evaluate(preludeValue)

    val value = parseAsBlock(code, interpreter.macroHandler)
    interpreter.evaluate(value)
  }

  def expect(actualCode: String, expectedCode: String): Unit = {
    assert(s"{ ${eval(actualCode).inspect} }" == parseAsBlock(expectedCode).inspect)
  }

  def expectFail(actualCode: String, message: String): Unit = {
    val evalError = intercept[EvalError] { eval(actualCode) }

    assert(evalError.message == message);
  }

  def expect(prelude: String, actualCode: String, expectedCode: String): Unit = {
    assert(s"{ ${eval(prelude, actualCode).inspect} }" == parseAsBlock(expectedCode).inspect)
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
    expect("(a){ a + 41 }(1)", "42")
    expect("(a){ a + 41 }.call 1", "42")
  }

  test("can partially evaluate code") {
    expect("(a) { 1 + 41 + a }", "(a) { 42 + a }")
    expect("(a) { a + (1 + 41) }", "(a) { a + 42 }")
  }

  test("assignment") {
    expect("answer = 42; answer", "42")
  }

  test("closures") {
    expect("(a){ (b){ a + b } }(1)(41)", "42")
  }

  test("nested usages of variables") {
    expect("(a){ { { a } } }(42)", "{ { 42 } }")
    expect("(a){ { { a + 1 } } }(41)", "{ { 42 } }")
    expect("(a){ $? + { a } }(42)", "$? + { 42 }")
  }

//  test("partial evaluation should not inline multiple times") {
//    expect("{ |a| { |b| a + b + a } }(42)", "{ |a| { |b| a + b + a } }(42)")
//  }

  test("evaluation of partial lambdas") {
    expect("(a){ (b){ a + b }(42) }", "(a){ a + 42 }")
  }

  test("higher-order functions") {
    expect("(fn){ fn(1) }((a){ a + 41 })", "42")
    expect("(fn){ fn(1) }((a){ b = a; b + 41 })", "42")
  }

  test("assignment transform") {
    val block = parseAsBlock("inc = (a){ a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?")

    AssignmentTransform.transform(block, None).inspect should equal("{ (call (lambda [(param inc)] { (call (lambda [(param a)] { (call (lambda [(param b)] { (+ (+ (inc self a) (inc self b)) $?) }) (+ a 1)) }) 3) }) (lambda [(param a)] { (+ a 1) })) }")
  }

  test("partial evaluation with unknowns") {
    expect("(a){ a + 1 + $? }(3)", "4 + $?")
    expect("a = 3; b = a + 1; a + b + $?", "7 + $?")
    expect("(fn){ fn(1) + fn(2) + $? }((a){ a + 1 })", "5 + $?")
    expect("inc = (a){ a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?", "9 + $?")
    expect("fn = (a){ a + 1 }; fn(1) + fn(2) + $?", "5 + $?")
  }

  test("advanced partial evaluation") {
    expect("(a){ (b){ (c){ a + b + c }(2) }($?) }(1)", "(b){ 1 + b + 2 }($?)")
    expect("a = 1; b = $?; c = 2; a + b + c", "(b){ 1 + b + 2 }($?)")
  }

  test("simple macros") {
    expect(
      "Core.define_macro('add_one', (parser) { e = parser.parse_next(); #e + 1 })",
      "add_one $?",

      "$? + 1"
    )

    expect(
      "Core.define_macro('add_one', (parser) { e = parser.parse_next(); #e + 1 })",
      "(a){ add_one(a + 2) }",

      "(a){ a + 2 + 1 }"
    )

    expect(
      "Core.define_macro('add_one', (parser) { e = parser.parse_next(); 42 })",
      "(a){ add_one(a + 2) }",

      "(a){ 42 }"
    )
  }

  test("named arguments") {
    expect("(a) { 41 + a }(a = 1)", "42")
    expect("(a, b) { 41 - a + b }(1, b = 2)", "42")
    expect("(a, b, c) { (20 - a + b) * c }(1, b = 2, c = 2)", "42")
  }

  test("simple structs") {
    expect("Struct(a = 42).a", "42")
    expect("struct = Struct(a = 42); struct.a", "42")
    expectFail("struct = Struct(a = 42); struct.b", "Cannot call method b on Struct(a = 42)")
  }

  // TODO: Implement this
//  test("calling methods on structs") {
//    expect("${ a = { 42 } }.a", "42")
//    expect("${ a = { 42 } }.a()", "42")
//    expect("${ a = { 42 } }.a.call", "42")
//    expect("${ a = { 42 } }.a()", "42")
//  }

//  test("method handler on structs") {
//    expect("struct = ${ $method: (name){ { name } } }; struct.hello", "'hello'")
//  }

//  test("self value in struct") {
//    expect("struct = ${ answer: 42, giveAnswer: { self.answer } }; struct.giveAnswer", "42")
//    expect(
//      "struct = ${ answer: 42, giveAnswer: { self.answer } }; " +
//        "giveAnswer = struct.$method('giveAnswer'); " +
//        "giveAnswer()"
//      , "42")
//  }
}
