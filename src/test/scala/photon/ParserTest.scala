package photon

import org.scalatest.FunSuite

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

  test("unknown literal") {
    assert(parse("$?") == "$?")
  }

  test("negating expressions") {
    assert(parse("-test") == "(- test)")
    assert(parse("-   5 + 5") == "(+ (- 5) 5)")
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
    assert(parse("a = 15") == "($assign a 15)")
    assert(parse("a = 5 * 5") == "($assign a (* 5 5))")
  }

  test("prefix operators") {
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
    assert(parse("a =\n\n 5 * 5") == "($assign a (* 5 5))")

    assert(parse("1 + 2 - 5") == "(- (+ 1 2) 5)")
    assert(parse("1 + 2 \n - 5") == "(+ 1 2) (- 5)")

    assert(parse("1 +\n\n 2 * \n\n 3") == "(+ 1 (* 2 3))")

//    TODO
//    parse_error("1 +\n\n 2 \n * \n\n 3");
  }

  test("names") {
    assert(parse("test \n test_two \n test3") == "test test_two test3")
    assert(parse("@test \n @test_two \n @test3") == "@test @test_two @test3")
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
    assert(parse("method(a, \n\n b,\n c\n) d") == "(method self a b c) d")
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
    assert(parse("{ |a, b| a\n b\n }") == "(lambda [(param a) (param b)] { a b })")
    assert(parse("{ a\n }.call 42") == "(call (lambda [] { a }) 42)")
    assert(parse("{ a\n }(42)") == "(call (lambda [] { a }) 42)")
  }

  test("nested lambda calls") {
    assert(parse("{ |a| { |b| a + b } }(1)(41)") == "(call (call (lambda [(param a)] { (lambda [(param b)] { (+ a b) }) }) 1) 41)");
  }

  test("structs") {
    assert(parse("${}") == "${}")
    assert(parse("${a: 1}") == "${a: 1}")

    // TODO: Fix order
    assert(parse("${a: 1, b: 2}") == "${a: 1, b: 2}")
  }
}
