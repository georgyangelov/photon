package photon

import org.scalatest.FunSuite

class LexerTest extends FunSuite {
  def lex(code: String): String = {
    new Lexer("<testing>", code).readAll()
      .map(_.inspect)
      .mkString(" ")
  }

  test("EOF") {
    assert(lex("") == "(EOF)")
  }

  test("skip whitespace") {
    assert(lex("   \t 12 \n ") == "(Number '12') (NewLine) (EOF)")
  }

  test("inline comments") {
    assert(lex("# test") == "(EOF)")
    assert(lex("# # # # # test") == "(EOF)")
    assert(lex("# test \n 1234") == "(Number '1234') (EOF)")
  }

  test("simple tokens") {
    assert(
      lex("()[],|.:12345\"test\"\ndoend") ==
      "(OpenParen) (CloseParen) (OpenBracket) (CloseBracket) (Comma) (Pipe) (Dot) (Colon) (Number '12345') (String 'test') (NewLine) (Name 'doend') (EOF)"
    )
  }

  test("single quote strings") {
    assert(lex("'test'") == "(String 'test') (EOF)")
    assert(lex("'te\\ns\\t'") == "(String 'te\\ns\\t') (EOF)")
    assert(lex("'te\\'st\\\\'") == "(String 'te'st\\') (EOF)")
  }

  test("numbers") {
    assert(lex("1234") == "(Number '1234') (EOF)")
    assert(lex("1234.5678") == "(Number '1234.5678') (EOF)")
  }

  test("literals") {
    // asselex("nil") == rt("(Nil) (EOF)")
    assert(lex("true") == "(Bool 'true') (EOF)")
    assert(lex("false") == "(Bool 'false') (EOF)")
  }

  test("calls on literals") {
    assert(lex("42.2.abs") == "(Number '42.2') (Dot) (Name 'abs') (EOF)")
    assert(lex("42.abs") == "(Number '42') (Dot) (Name 'abs') (EOF)")
    assert(
      lex("\"test\".length 3")
      ==
      "(String 'test') (Dot) (Name 'length') (Number '3') (EOF)"
    )
  }

  test("escape sequences") {
    assert(lex("\"a\\nb\\\\c\\td\"") == "(String 'a\nb\\c\td') (EOF)")
  }

  test("unicode") {
    assert(lex("тест \"тест\"") == "(Name 'тест') (String 'тест') (EOF)")
  }

  test("underscores in names") {
    assert(lex(" _this_is_a_valid_name ") == "(Name '_this_is_a_valid_name') (EOF)")
  }

  test("special symbols in names") {
    assert(lex("$a_special_variable") == "(Name '$a_special_variable') (EOF)")
    assert(lex("@an_instance_variable") == "(Name '@an_instance_variable') (EOF)")
    assert(lex("valid_name? valid_name!") == "(Name 'valid_name?') (Name 'valid_name!') (EOF)")

    assert(
      lex("invalid_nam!e invalid_n?ame")
      ==
      "(Name 'invalid_nam!') (Name 'e') (Name 'invalid_n?') (Name 'ame') (EOF)"
    )
  }

//  test("test_error_on_unknown_token") {
//    lex_error("test %")
//  }

  test("braces") {
    assert(lex("{}") == "(OpenBrace) (CloseBrace) (EOF)")
  }

  test("unary operators") {
    assert(lex("!") == "(UnaryOperator '!') (EOF)")
    assert(lex("!!") == "(UnaryOperator '!') (UnaryOperator '!') (EOF)")
  }

  test("binary operators") {
    assert(lex("a== b") == "(Name 'a') (BinaryOperator '==') (Name 'b') (EOF)")
    assert(lex("+ -") == "(BinaryOperator '+') (BinaryOperator '-') (EOF)")
    assert(lex("and or") == "(BinaryOperator 'and') (BinaryOperator 'or') (EOF)")
    assert(lex("* /") == "(BinaryOperator '*') (BinaryOperator '/') (EOF)")
    assert(lex("< >") == "(BinaryOperator '<') (BinaryOperator '>') (EOF)")
    assert(lex("<= >= !=") == "(BinaryOperator '<=') (BinaryOperator '>=') (BinaryOperator '!=') (EOF)")
    assert(lex("+= -=") == "(BinaryOperator '+=') (BinaryOperator '-=') (EOF)")
    assert(lex("/= *=") == "(BinaryOperator '/=') (BinaryOperator '*=') (EOF)")
    assert(lex("a = b") == "(Name 'a') (BinaryOperator '=') (Name 'b') (EOF)")
  }

  test("method resolves") {
    assert(lex("a::b") == "(Name 'a') (DoubleColon) (Name 'b') (EOF)")
  }
}
