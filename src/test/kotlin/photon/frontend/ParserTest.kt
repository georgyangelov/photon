package photon.frontend

import org.junit.jupiter.api.Assertions.*
import kotlin.test.*

internal class ParserTest {
  @Test
  fun testNumberLiterals() {
    assertParse("12345 ", "12345")
    assertParse("1234.5678 ", "1234.5678")
  }

  @Test
  fun testNegativeNumberLiterals() {
    assertParse("-1234", "(- 1234)")
    assertParse("-1234.5", "(- 1234.5)")
  }

  @Test
  fun testConstantObjectLiterals() {
    assertParse("true", "true")
    assertParse("false", "false")
    assertParse("nil", "nil")
  }

  @Test
  fun testNegatingExpressions() {
    //assertParse("-test", "(- test)")
    //assertParse("-   5 + 5", "(+ (- 5) 5)")
    assertParse("-   (5 + 5)", "(- (+ 5 5))")
  }

  @Test
  fun testStringLiterals() {
    assertParse("\"Hello world!\"", "\"Hello world!\"")
    assertParse("\"\\\"\\n\"", "\"\\\"\\n\"")
  }

  @Test
  fun testInfixOperators() {
    assertParse("1 + 2 + 3 + 4", "(+ (+ (+ 1 2) 3) 4)")
    assertParse("1 * 2 * 3 * 4", "(* (* (* 1 2) 3) 4)")
    assertParse("1 - 2 - 3 - 4", "(- (- (- 1 2) 3) 4)")
    assertParse("1 / 2 / 3 / 4", "(/ (/ (/ 1 2) 3) 4)")
    assertParse("1 or 2 and 3 or 4", "(or (or 1 (and 2 3)) 4)")
  }

  @Test
  fun testAssignment() {
    assertParse("val a = 15", "(let a 15 {})")
    assertParse("val a = 5 * 5", "(let a (* 5 5) {})")
  }

  @Test
  fun testPrefixOperators() {
    assertParse("#a", "(# a)")
    assertParse("#(a)", "(# a)")
    assertParse("!a", "(! a)")
    assertParse("!!a", "(! (! a))")
  }

  @Test
  fun testOperatorPrecedence() {
    assertParse("1 + 2 * 3", "(+ 1 (* 2 3))")

    assertParse("1 == 2 + 2 * 3", "(== 1 (+ 2 (* 2 3)))")
    assertParse("1 == 2 * 2 + 3", "(== 1 (+ (* 2 2) 3))")
    assertParse("1 != 2 * 2 + 3", "(!= 1 (+ (* 2 2) 3))")
    assertParse("1 <= 2 * 2 + 3", "(<= 1 (+ (* 2 2) 3))")
    assertParse("1 >= 2 * 2 + 3", "(>= 1 (+ (* 2 2) 3))")
    assertParse("1 < 2 * 2 + 3", "(< 1 (+ (* 2 2) 3))")
    assertParse("1 > 2 * 2 + 3", "(> 1 (+ (* 2 2) 3))")

    assertParse("1 - 2 / 3 * 4 + 5", "(+ (- 1 (* (/ 2 3) 4)) 5)")
  }

  @Test
  fun testParensForPrecedence() {
    assertParse("(1 + 2) * 3", "(* (+ 1 2) 3)")
  }

  @Test
  fun testUnaryOperatorPrecedence() {
    assertParse("1 + 2 * !a", "(+ 1 (* 2 (! a)))")
    assertParse("!1 + 2 * a", "(+ (! 1) (* 2 a))")
  }

  @Test
  fun testNewlinesInExpressions() {
    assertParse("val a =\n\n 5 * 5", "(let a (* 5 5) {})")

    assertParse("1 + 2 - 5", "(- (+ 1 2) 5)")
    assertParse("1 + 2 \n - 5", "(+ 1 2) (- 5)")

    assertParse("1 +\n\n 2 * \n\n 3", "(+ 1 (* 2 3))")

//    TODO
//    parse_error("1 +\n\n 2 \n * \n\n 3");
  }

  @Test
  fun testNames() {
    assertParse("test \n test_two \n test3", "test test_two test3")
    assertParse("@test \n @test_two \n @test3", "@test @test_two @test3")
    assertParse("asdf\$test \n \$test_two", "asdf\$test \$test_two")
  }

  @Test
  fun testMethodCalls() {
    assertParse("method", "method")
    assertParse("method()", "(method self)")
    assertParse("target.method", "(method target)")
    assertParse("target.method()", "(method target)")

    assertParse("a()", "(a self)")
  }

  @Test
  fun testMethodCallsWithArguments() {
    assertParse("method(a)", "(method self a)")
    assertParse("method a", "(method self a)")

    assertParse("method(a, b, c)", "(method self a b c)")
    assertParse("method a, b, c", "(method self a b c)")

    assertParse("method a, \n\n b,\n c", "(method self a b c)")

    assertParse("one.method a, \n\n b,\n c\n two.d", "(method one a b c) (d two)")

    assertParse("target.method(a)", "(method target a)")
    assertParse("target.method a", "(method target a)")

    assertParse("target.method(a, b, c)", "(method target a b c)")
    assertParse("target.method a, b, c", "(method target a b c)")
  }

  @Test
  fun testMethodsWithOperatorNames() {
    assertParse("true.!()", "(! true)")
    assertParse("1.+(42)", "(+ 1 42)")
    assertParse("1.*(42)", "(* 1 42)")
    assertParse("1.==(42)", "(== 1 42)")
    assertParse("1.+=(42)", "(+= 1 42)")
    assertParse("true.and(false)", "(and true false)")
  }

  @Test
  fun testMethodChaining() {
    assertParse("a.b.c", "(c (b a))")
    assertParse("a.b.c.d e", "(d (c (b a)) e)")
    assertParse("a.b(1).c", "(c (b a 1))")
    assertParse("a.b(1).c 2, 3", "(c (b a 1) 2 3)")
    assertParse("a.b(1).c 2.d, d(3, 4)", "(c (b a 1) (d 2) (d self 3 4))")
  }

  @Test
  fun testArgumentAssociativity() {
    assertParse("one two a, b", "(one self (two self a b))")
    assertParse("one two(a), b", "(one self (two self a) b)")
    assertParse("one(two a, b)", "(one self (two self a b))")
    assertParse("one two(a, b), c, d", "(one self (two self a b) c d)")

    assertParse("method a, \n\n b,\n c\n d", "(method self a b c) d")
    assertParse("method(a, \n\n b,\n c\n); d", "(method self a b c) d")
    assertParse("method a, \n\n b,\n c d e f", "(method self a b (c self (d self (e self f))))")
  }

  @Test
  fun testMethodCallPriority() {
    assertParse("one a + b", "(one self (+ a b))")
    assertParse("one(a) + b", "(+ (one self a) b)")

    assertParse("one a, b + c", "(one self a (+ b c))")
    assertParse("one(a, b) + c", "(+ (one self a b) c)")
  }

//  TODO
//  @Test
//  fun test("method call line completeness") {
//    parse_error("call 1234 b");
//    parse_error("call \"test\" b");
//  }

  @Test
  fun testLambdas() {
    assertParse("{ a\n b\n }", "(lambda [] { a b })")
    assertParse("(a, b) { a\n b\n }", "(lambda [(param a) (param b)] { a b })")
    assertParse("{ a\n }.call 42", "(call (lambda [] a) 42)")
    assertParse("{ a\n }(42)", "(call (lambda [] a) 42)")
  }

  @Test
  fun testLambdasWithASingleExpression() {
    assertParse("(a, b) a + b", "(lambda [(param a) (param b)] (+ a b))")
    assertParse("((a, b) a + b)()", "(call (lambda [(param a) (param b)] (+ a b)))")

    // TODO: These should be errors
    // assertParse("(a, b) (a + b)", "(lambda [(param a) (param b)] { (+ a b) })")
    // assertParse("((a, b) (a + b))()", "(call (lambda [(param a) (param b)] { (+ a b) }))")
    // assertParse("((a, b) (a) + (b))()", "(call (lambda [(param a) (param b)] { (+ a b) }))")
    // assertParse("((a, b) ((a) + (b)))()", "(call (lambda [(param a) (param b)] { (+ a b) }))")

    assertParse("() a + b", "(lambda [] (+ a b))")
    assertParse("(() a + b)()", "(call (lambda [] (+ a b)))")
  }

  @Test
  fun testAmbiguousLambdaCases() {
    assertParse("array.forEach (element) { element + 1 }", "(forEach array (lambda [(param element)] (+ element 1)))")
    assertParse("forEach (element) { element + 1 }", "(forEach self (lambda [(param element)] (+ element 1)))")

    // TODO: These should be errors
    // assertParse("array.forEach(element) { element + 1 }", "(forEach array (lambda [(param element)] { (+ element 1) }))")
    // assertParse("forEach (element) { element + 1 }", "(forEach self (lambda [(param element)] { (+ element 1) }))")

    assertParse("array.forEach(element)", "(forEach array element)")
    assertParse("forEach(element)", "(forEach self element)")

    assertParse("(a){ a + 41 }(1)", "(call (lambda [(param a)] (+ a 41)) 1)")
    assertParse("((a){ a + 41 })(1)", "(call (lambda [(param a)] (+ a 41)) 1)")

    // TODO: This is an error, the argument list must not have whitespace before the open paren
    // assertParse("(a){ a + 41 } (1)", "(lambda [(param a)] { (+ a 41) }) 1")
  }

  @Test
  fun testNestedLambdaCalls() {
    assertParse("(a) { (b) { a + b } }(1)(41)", "(call (call (lambda [(param a)] (lambda [(param b)] (+ a b))) 1) 41)")
  }

  @Test
  fun testNamedArguments() {
    assertEquals(parse("fn(a = 1)"), "(fn self (param a 1))")
    assertEquals(parse("fn(a = 1, b = 2)"), "(fn self (param a 1) (param b 2))")

    // TODO: These should be an error
//    assertThrows[ParseError] { parse("fn(1, a = 2, 3)") }
//    assertThrows[ParseError] { parse("fn(a = 2, 3)") }
  }

  @Test
  fun testNamedArgumentsWithoutParens() {
    assertParse("fn a = 1", "(fn self (param a 1))")
    assertParse("fn a = 1, b = 2", "(fn self (param a 1) (param b 2))")
  }

  @Test
  fun testMixedNamedArguments() {
    assertParse("fn(42, a = 1)", "(fn self 42 (param a 1))")
    assertParse("fn 42, a = 1", "(fn self 42 (param a 1))")
  }

  @Test
  fun testLambdasUsingOnlyBraces() {
    assertParse("{ a }", "(lambda [] a)")
    assertParse("val a = 42; { a }", "(let a 42 (lambda [] a))")
  }

  @Test
  fun testDirectCallOnLambdaWithNamedArguments() {
    assertParse("(a, b) { a + b }(1, b = 2)", "(call (lambda [(param a) (param b)] (+ a b)) 1 (param b 2))")
    assertParse("(a, b) { a + b }(a = 1, b = 2)", "(call (lambda [(param a) (param b)] (+ a b)) (param a 1) (param b 2))")
  }

  @Test
  fun testTypeAnnotationsOnValues() {
    assertParse("42: Int", "(typeCheck Core 42 Int)")
    assertParse("a: Int", "(typeCheck Core a Int)")
    assertParse("\"hello\": String", "(typeCheck Core \"hello\" String)")
    assertParse("42: Map(String, List(Int))", "(typeCheck Core 42 (Map self String (List self Int)))")

    // TODO: What about this?
    // assertParse("fn(42): Int", "(typeCheck Core (fn self 42) Int)")
  }

  @Test
  fun testTypeAnnotationsOnFunctionParameters() {
    assertParse("(a: Int) a + 1", "(lambda [(param a Int)] (+ a 1))")
    assertParse("(a: Int, b: String) a + b", "(lambda [(param a Int) (param b String)] (+ a b))")
    assertParse("(a: List(Int), b: Map(String, List(Int))) 42", "(lambda [(param a (List self Int)) (param b (Map self String (List self Int)))] 42)")
  }

  @Test
  fun testRequiresParenthesesOnTypesOfParameters() {
    assertThrows(ParseError::class.java) { parse("(param1: fn arg1, b: Int) 42") }
  }

  @Test
  fun testPatternsInParameterTypes() {
    assertParse("(a: val AT) 42", "(lambda [(param a (val AT))] 42)")
    assertParse("(a: val AT, b: AT) 42", "(lambda [(param a (val AT)) (param b AT)] 42)")
  }

  @Test
  fun testCallPatternsInParameterTypes() {
    assertParse("(a: Optional(val T)) 42", "(lambda [(param a <Optional self (val T)>)] 42)")
    assertParse("(a: Optional.match(val T)) 42", "(lambda [(param a <match Optional (val T)>)] 42)")
    assertParse("(a: Optional.match(1, val T)) 42", "(lambda [(param a <match Optional 1 (val T)>)] 42)")
    assertParse("(a: Optional.match(1, Optional(val T))) 42", "(lambda [(param a <match Optional 1 <Optional self (val T)>>)] 42)")
  }

  @Test
  fun testSpecificValuePatternsInParameterTypes() {
    assertParse("(a: Optional(Int)) 42", "(lambda [(param a (Optional self Int))] 42)")
    assertParse("(a: Optional.of(Int)) 42", "(lambda [(param a (of Optional Int))] 42)")
    assertParse("(a: Optional.of(1, Int)) 42", "(lambda [(param a (of Optional 1 Int))] 42)")
  }

  @Test
  fun testParametersWithExternalAndInternalNames() {
    assertParse("(for as name: String, aged as age: Int) 42", "(lambda [(param for name String) (param aged age Int)] 42)")
  }

  @Test
  fun testTypeAnnotationsOnFunctionReturnType() {
    assertParse("(a: Int): Int { a + 1 }", "(lambda [(param a Int)] Int (+ a 1))")
    assertParse("(a: Int): Int a + 1", "(lambda [(param a Int)] Int (+ a 1))")
  }

  @Test
  fun testTypeAnnotationsOnVal() {
    assertParse("val a: Int = 42; a", "(let a (typeCheck Core 42 Int) a)")
    assertParse("val a: Stream(Int) = 42; a", "(let a (typeCheck Core 42 (Stream self Int)) a)")
  }

  @Test
  fun testParenthesesForBlocks() {
    assertParse("(a; b)", "{ a b }")
    assertParse("(a; b) + 1", "(+ { a b } 1)")
    assertParse("val a = 11; (val a = 42; () { a }) + a", "(let a 11 (+ (let a 42 (lambda [] a)) a))")

    assertThrows(ParseError::class.java) { parse("()") }
  }

  @Test
  fun testMethodCallPrecedence() {
    assertParse("array.map { 42 }.filter (x) x > 0", "(map array (filter (lambda [] 42) (lambda [(param x)] (> x 0))))")
    assertParse("array.map { 42 } .filter (x) x > 0", "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")
    assertParse("array.map({ 42 }).filter (x) x > 0", "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")
    assertParse("array.map { 42 }\n.filter (x) x > 0", "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")

    assertParse("array.map 42.filter", "(map array (filter 42))")
    assertParse("array.map(42 .filter)", "(map array (filter 42))")
    assertParse("array.map 42 .filter", "(filter (map array 42))")
    assertParse("array.map(42).filter", "(filter (map array 42))")

    assertParse("array.map 1 + 2.filter", "(map array (+ 1 (filter 2)))")
    assertParse("array.map 1 + 2 .filter", "(filter (map array (+ 1 2)))")
    assertParse("array.map 1.to_s + 2 .filter", "(filter (map array (+ (to_s 1) 2)))")

    assertParse("array.map array2.map 1 .filter", "(filter (map array (map array2 1)))")

    assertParse("map 42.filter", "(map self (filter 42))")
    assertParse("map(42 .filter)", "(map self (filter 42))")
    assertParse("map 42 .filter", "(filter (map self 42))")
    assertParse("map(42).filter", "(filter (map self 42))")
  }

  @Test
  fun testTypesForFunctionValues() {
    assertParse("val a: (): Int = () 42; a", "(let a (typeCheck Core (lambda [] 42) (Function [] Int)) a)")
    assertParse("val a: ((): Int) = () 42; a", "(let a (typeCheck Core (lambda [] 42) (Function [] Int)) a)")
  }

  @Test
  fun testFunctionTypesWithArgumentTypes() {
    assertParse("(a: Int): Int", "(Function [(param a Int)] Int)")
    assertParse("(a: Int, b: String): Int", "(Function [(param a Int) (param b String)] Int)")
  }

  @Test
  fun testGenericFunctionsWithPatternsInLambdaTypes() {
    assertParse("val fn = (a: (n: val T): T) a(42); fn((a) a)", "(let fn (lambda [(param a (Function [(param n (val T))] T))] (a self 42)) (fn self (lambda [(param a)] a)))")
  }

  @Test
  fun testGenericFunctionsWithPatternsInLambdaReturnType() {
    assertParse("val fn = (a: (): val T) a(); fn(() 42)", "(let fn (lambda [(param a (Function [] (val T)))] (a self)) (fn self (lambda [] 42)))")
  }

  private fun parse(code: String): String {
    return Parser(Lexer("<testing>", code), Parser.BlankMacroHandler)
      .parseAll()
      .joinToString(" ") { it.inspect() }
  }

  private fun assertParse(code: String, expected: String) {
    assertEquals(expected, parse(code))
  }
}