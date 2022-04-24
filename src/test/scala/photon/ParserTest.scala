package photon

import org.scalatest.FunSuite
import photon.frontend.{Lexer, ParseError, Parser}

class ParserTest extends FunSuite {
  def parse(code: String): String = {
    new Parser(new Lexer("<testing>", code), Parser.BlankMacroHandler)
      .parseAll()
      .map(_.inspect)
      .mkString(" ")
  }

  test("number literals") {
    assert(parse("12345 ") == "12345")
    assert(parse("1234.5678 ") == "1234.5678")
  }

  test("negative number literals") {
    assert(parse("-1234") == "(- 1234)")
    assert(parse("-1234.5") == "(- 1234.5)")
  }

  test("constant object literals") {
    assert(parse("true") == "true")
    assert(parse("false") == "false")
    assert(parse("nil") == "nil")
  }

  test("negating expressions") {
    //assert(parse("-test") == "(- test)")
    //assert(parse("-   5 + 5") == "(+ (- 5) 5)")
    assert(parse("-   (5 + 5)") == "(- (+ 5 5))")
  }

  test("string literals") {
    assert(parse("\"Hello world!\"") == "\"Hello world!\"")
    assert(parse("\"\\\"\\n\"") == "\"\\\"\\n\"")
  }

  test("infix operators") {
    assert(parse("1 + 2 + 3 + 4") == "(+ (+ (+ 1 2) 3) 4)")
    assert(parse("1 * 2 * 3 * 4") == "(* (* (* 1 2) 3) 4)")
    assert(parse("1 - 2 - 3 - 4") == "(- (- (- 1 2) 3) 4)")
    assert(parse("1 / 2 / 3 / 4") == "(/ (/ (/ 1 2) 3) 4)")
    assert(parse("1 or 2 and 3 or 4") == "(or (or 1 (and 2 3)) 4)")
  }

  test("assignment") {
    assert(parse("val a = 15") == "(let a 15 {})")
    assert(parse("val a = 5 * 5") == "(let a (* 5 5) {})")
  }

  test("prefix operators") {
    assert(parse("#a") == "(# a)")
    assert(parse("#(a)") == "(# a)")
    assert(parse("!a") == "(! a)")
    assert(parse("!!a") == "(! (! a))")
  }

  test("operator precedence") {
    assert(parse("1 + 2 * 3") == "(+ 1 (* 2 3))")

    assert(parse("1 == 2 + 2 * 3") == "(== 1 (+ 2 (* 2 3)))")
    assert(parse("1 == 2 * 2 + 3") == "(== 1 (+ (* 2 2) 3))")
    assert(parse("1 != 2 * 2 + 3") == "(!= 1 (+ (* 2 2) 3))")
    assert(parse("1 <= 2 * 2 + 3") == "(<= 1 (+ (* 2 2) 3))")
    assert(parse("1 >= 2 * 2 + 3") == "(>= 1 (+ (* 2 2) 3))")
    assert(parse("1 < 2 * 2 + 3") == "(< 1 (+ (* 2 2) 3))")
    assert(parse("1 > 2 * 2 + 3") == "(> 1 (+ (* 2 2) 3))")

    assert(parse("1 - 2 / 3 * 4 + 5") == "(+ (- 1 (* (/ 2 3) 4)) 5)")
  }

  test("parens for precedence") {
    assert(parse("(1 + 2) * 3") == "(* (+ 1 2) 3)")
  }

  test("unary operator precedence") {
    assert(parse("1 + 2 * !a") == "(+ 1 (* 2 (! a)))")
    assert(parse("!1 + 2 * a") == "(+ (! 1) (* 2 a))")
  }

  test("newlines in expressions") {
    assert(parse("val a =\n\n 5 * 5") == "(let a (* 5 5) {})")

    assert(parse("1 + 2 - 5") == "(- (+ 1 2) 5)")
    assert(parse("1 + 2 \n - 5") == "(+ 1 2) (- 5)")

    assert(parse("1 +\n\n 2 * \n\n 3") == "(+ 1 (* 2 3))")

//    TODO
//    parse_error("1 +\n\n 2 \n * \n\n 3");
  }

  test("names") {
    assert(parse("test \n test_two \n test3") == "test test_two test3")
    assert(parse("@test \n @test_two \n @test3") == "@test @test_two @test3")
    assert(parse("asdf$test \n $test_two") == "asdf$test $test_two")
  }

  test("method calls") {
    assert(parse("method") == "method")
    assert(parse("method()") == "(method self)")
    assert(parse("target.method") == "(method target)")
    assert(parse("target.method()") == "(method target)")

    assert(parse("a()") == "(a self)")
  }

//  test("subname resolution") {
//    assert(parse("A::method") == "(A::method)")
//    assert(parse("A.B::method") == "((B A)::method)")
//    assert(parse("NameA.NameB") == "(NameB NameA)")
//    assert(parse("NameA.NameB.test") == "(test (NameB NameA))")
//    assert(parse("NameA.NameB.test 123") == "(test (NameB NameA) 123)")
//    assert(parse("A.include(B)::C::D") == "(((include A B)::C)::D)")
//    assert(parse("A.include(B)::C::D.test") == "(test (((include A B)::C)::D))")
//  }

  test("method calls with arguments") {
    assert(parse("method(a)") == "(method self a)")
    assert(parse("method a") == "(method self a)")

    assert(parse("method(a, b, c)") == "(method self a b c)")
    assert(parse("method a, b, c") == "(method self a b c)")

    assert(parse("method a, \n\n b,\n c") == "(method self a b c)")

    assert(parse("one.method a, \n\n b,\n c\n two.d") == "(method one a b c) (d two)")

    assert(parse("target.method(a)") == "(method target a)")
    assert(parse("target.method a") == "(method target a)")

    assert(parse("target.method(a, b, c)") == "(method target a b c)")
    assert(parse("target.method a, b, c") == "(method target a b c)")
  }

  test("methods with operator names") {
    assert(parse("true.!()") == "(! true)")
    assert(parse("1.+(42)") == "(+ 1 42)")
    assert(parse("1.*(42)") == "(* 1 42)")
    assert(parse("1.==(42)") == "(== 1 42)")
    assert(parse("1.+=(42)") == "(+= 1 42)")
    assert(parse("true.and(false)") == "(and true false)")
  }

  test("method chaining") {
    assert(parse("a.b.c") == "(c (b a))")
    assert(parse("a.b.c.d e") == "(d (c (b a)) e)")
    assert(parse("a.b(1).c") == "(c (b a 1))")
    assert(parse("a.b(1).c 2, 3") == "(c (b a 1) 2 3)")
    assert(parse("a.b(1).c 2.d, d(3, 4)") == "(c (b a 1) (d 2) (d self 3 4))")
  }

  test("argument associativity") {
    assert(parse("one two a, b") == "(one self (two self a b))")
    assert(parse("one two(a), b") == "(one self (two self a) b)")
    assert(parse("one(two a, b)") == "(one self (two self a b))")
    assert(parse("one two(a, b), c, d") == "(one self (two self a b) c d)")

    assert(parse("method a, \n\n b,\n c\n d") == "(method self a b c) d")
    assert(parse("method(a, \n\n b,\n c\n); d") == "(method self a b c) d")
    assert(parse("method a, \n\n b,\n c d e f") == "(method self a b (c self (d self (e self f))))")
  }

  test("method call priority") {
    assert(parse("one a + b") == "(one self (+ a b))")
    assert(parse("one(a) + b") == "(+ (one self a) b)")

    assert(parse("one a, b + c") == "(one self a (+ b c))")
    assert(parse("one(a, b) + c") == "(+ (one self a b) c)")
  }

//  TODO
//  test("method call line completeness") {
//    parse_error("call 1234 b");
//    parse_error("call \"test\" b");
//  }

  test("lambdas") {
    assert(parse("{ a\n b\n }") == "(lambda [] { a b })")
    assert(parse("(a, b) { a\n b\n }") == "(lambda [(param a) (param b)] { a b })")
    assert(parse("{ a\n }.call 42") == "(call (lambda [] a) 42)")
    assert(parse("{ a\n }(42)") == "(call (lambda [] a) 42)")
  }

  test("lambdas with a single expression") {
    assert(parse("(a, b) a + b") == "(lambda [(param a) (param b)] (+ a b))")
    assert(parse("((a, b) a + b)()") == "(call (lambda [(param a) (param b)] (+ a b)))")

    // TODO: These should be errors
    // assert(parse("(a, b) (a + b)") == "(lambda [(param a) (param b)] { (+ a b) })")
    // assert(parse("((a, b) (a + b))()") == "(call (lambda [(param a) (param b)] { (+ a b) }))")
    // assert(parse("((a, b) (a) + (b))()") == "(call (lambda [(param a) (param b)] { (+ a b) }))")
    // assert(parse("((a, b) ((a) + (b)))()") == "(call (lambda [(param a) (param b)] { (+ a b) }))")

    assert(parse("() a + b") == "(lambda [] (+ a b))")
    assert(parse("(() a + b)()") == "(call (lambda [] (+ a b)))")
  }

  test("Ambiguous lambda cases") {
    assert(parse("array.forEach (element) { element + 1 }") == "(forEach array (lambda [(param element)] (+ element 1)))")
    assert(parse("forEach (element) { element + 1 }") == "(forEach self (lambda [(param element)] (+ element 1)))")

    // TODO: These should be errors
    // assert(parse("array.forEach(element) { element + 1 }") == "(forEach array (lambda [(param element)] { (+ element 1) }))")
    // assert(parse("forEach (element) { element + 1 }") == "(forEach self (lambda [(param element)] { (+ element 1) }))")

    assert(parse("array.forEach(element)") == "(forEach array element)")
    assert(parse("forEach(element)") == "(forEach self element)")

    assert(parse("(a){ a + 41 }(1)") == "(call (lambda [(param a)] (+ a 41)) 1)")
    assert(parse("((a){ a + 41 })(1)") == "(call (lambda [(param a)] (+ a 41)) 1)")

    // TODO: This is an error, the argument list must not have whitespace before the open paren
    // assert(parse("(a){ a + 41 } (1)") == "(lambda [(param a)] { (+ a 41) }) 1")
  }

  test("nested lambda calls") {
    assert(parse("(a) { (b) { a + b } }(1)(41)") == "(call (call (lambda [(param a)] (lambda [(param b)] (+ a b))) 1) 41)")
  }

  test("named arguments") {
    assert(parse("fn(a = 1)") == "(fn self (param a 1))")
    assert(parse("fn(a = 1, b = 2)") == "(fn self (param a 1) (param b 2))")

    // TODO: These should be an error
//    assertThrows[ParseError] { parse("fn(1, a = 2, 3)") }
//    assertThrows[ParseError] { parse("fn(a = 2, 3)") }
  }

  test("named arguments without parens") {
    assert(parse("fn a = 1") == "(fn self (param a 1))")
    assert(parse("fn a = 1, b = 2") == "(fn self (param a 1) (param b 2))")
  }

  test("mixed named arguments") {
    assert(parse("fn(42, a = 1)") == "(fn self 42 (param a 1))")
    assert(parse("fn 42, a = 1") == "(fn self 42 (param a 1))")
  }

  test("direct call on lambda with named arguments") {
    assert(parse("(a, b) { a + b }(1, b = 2)") == "(call (lambda [(param a) (param b)] (+ a b)) 1 (param b 2))")
    assert(parse("(a, b) { a + b }(a = 1, b = 2)") == "(call (lambda [(param a) (param b)] (+ a b)) (param a 1) (param b 2))")
  }

  test("type annotations on values") {
    assert(parse("42: Int") == "(typeCheck Core 42 Int)")
    assert(parse("a: Int") == "(typeCheck Core a Int)")
    assert(parse("\"hello\": String") == "(typeCheck Core \"hello\" String)")
    assert(parse("42: Map(String, List(Int))") == "(typeCheck Core 42 (Map self String (List self Int)))")

    // TODO: What about this?
    // assert(parse("fn(42): Int") == "(typeCheck Core (fn self 42) Int)")
  }

  test("type annotations on function parameters") {
    assert(parse("(a: Int) a + 1") == "(lambda [(param a Int)] (+ a 1))")
    assert(parse("(a: Int, b: String) a + b") == "(lambda [(param a Int) (param b String)] (+ a b))")
    assert(parse("(a: List(Int), b: Map(String, List(Int))) 42") == "(lambda [(param a (List self Int)) (param b (Map self String (List self Int)))] 42)")
  }

  test("requires parentheses on types of parameters") {
    assertThrows[ParseError] { parse("(param1: fn arg1, b: Int) 42") }
  }

  test("patterns in parameter types") {
    assert(parse("(a: val AT) 42") == "(lambda [(param a (val AT))] 42)")
    assert(parse("(a: val AT, b: AT) 42") == "(lambda [(param a (val AT)) (param b AT)] 42)")
  }

  test("parameters with external and internal names") {
    assert(parse("(for as name: String, aged as age: Int) 42") == "(lambda [(param for name String) (param aged age Int)] 42)")
  }

  test("type annotations on function return type") {
    assert(parse("(a: Int): Int { a + 1 }") == "(lambda [(param a Int)] Int (+ a 1))")
    assert(parse("(a: Int): Int a + 1") == "(lambda [(param a Int)] Int (+ a 1))")
  }

  test("type annotations on val") {
    assert(parse("val a: Int = 42; a") == "(let a (typeCheck Core 42 Int) a)")
    assert(parse("val a: Stream(Int) = 42; a") == "(let a (typeCheck Core 42 (Stream self Int)) a)")
  }

  test("parentheses for blocks") {
    assert(parse("(a; b)") == "{ a b }")
    assert(parse("(a; b) + 1") == "(+ { a b } 1)")
    assert(parse("val a = 11; (val a = 42; () { a }) + a") == "(let a 11 (+ (let a 42 (lambda [] a)) a))")

    assertThrows[ParseError] { parse("()") }
  }

  test("method call precedence") {
    assert(parse("array.map { 42 }.filter (x) x > 0") == "(map array (filter (lambda [] 42) (lambda [(param x)] (> x 0))))")
    assert(parse("array.map { 42 } .filter (x) x > 0") == "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")
    assert(parse("array.map({ 42 }).filter (x) x > 0") == "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")
    assert(parse("array.map { 42 }\n.filter (x) x > 0") == "(filter (map array (lambda [] 42)) (lambda [(param x)] (> x 0)))")

    assert(parse("array.map 42.filter") == "(map array (filter 42))")
    assert(parse("array.map(42 .filter)") == "(map array (filter 42))")
    assert(parse("array.map 42 .filter") == "(filter (map array 42))")
    assert(parse("array.map(42).filter") == "(filter (map array 42))")

    assert(parse("array.map 1 + 2.filter") == "(map array (+ 1 (filter 2)))")
    assert(parse("array.map 1 + 2 .filter") == "(filter (map array (+ 1 2)))")
    assert(parse("array.map 1.to_s + 2 .filter") == "(filter (map array (+ (to_s 1) 2)))")

    assert(parse("array.map array2.map 1 .filter") == "(filter (map array (map array2 1)))")

    assert(parse("map 42.filter") == "(map self (filter 42))")
    assert(parse("map(42 .filter)") == "(map self (filter 42))")
    assert(parse("map 42 .filter") == "(filter (map self 42))")
    assert(parse("map(42).filter") == "(filter (map self 42))")
  }
}
