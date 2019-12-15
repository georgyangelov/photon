package photon

import org.scalatest.FunSuite

class LexerTest extends FunSuite {
  def lex(code: String): String = {
    new Lexer("<testing>", code).readAll()
      .map(_.inspect)
      .mkString(" ")
  }

  test("test_eof") {
    assert(lex("") == "(EOF)")
  }

  test("test_skip_whitespace") {
    assert(lex("   \t 12 \n ") == "(Number '12') (NewLine) (EOF)")
  }

  test("test_inline_comments") {
    assert(lex("# test") == "(EOF)")
    assert(lex("# # # # # test") == "(EOF)")
    assert(lex("# test \n 1234") == "(Number '1234') (EOF)")
  }

  test("test_simple_tokens") {
    assert(
      lex("()[],|.:12345\"test\"\ndoend") ==
      "(OpenParen) (CloseParen) (OpenBracket) (CloseBracket) (Comma) (Pipe) (Dot) (Colon) (Number '12345') (String 'test') (NewLine) (Name 'doend') (EOF)"
    )
  }

  test("test_single_quote_strings") {
    assert(lex("'test'") == "(String 'test') (EOF)")
    assert(lex("'te\\ns\\t'") == "(String 'te\\ns\\t') (EOF)")
    assert(lex("'te\\'st\\\\'") == "(String 'te'st\\') (EOF)")
  }

  test("test_numbers") {
    assert(lex("1234") == "(Number '1234') (EOF)")
    assert(lex("1234.5678") == "(Number '1234.5678') (EOF)")
  }

  test("test_literals") {
    // asselex("nil") == rt("(Nil) (EOF)")
    assert(lex("true") == "(Bool 'true') (EOF)")
    assert(lex("false") == "(Bool 'false') (EOF)")
  }

  test("test_calls_on_literals") {
    assert(lex("42.2.abs") == "(Number '42.2') (Dot) (Name 'abs') (EOF)")
    assert(lex("42.abs") == "(Number '42') (Dot) (Name 'abs') (EOF)")
    assert(
      lex("\"test\".length 3")
      ==
      "(String 'test') (Dot) (Name 'length') (Number '3') (EOF)"
    )
  }

  test("test_escape_sequences") {
    assert(lex("\"a\\nb\\\\c\\td\"") == "(String 'a\nb\\c\td') (EOF)")
  }

  test("test_unicode") {
    assert(lex("тест \"тест\"") == "(Name 'тест') (String 'тест') (EOF)")
  }

  test("test_underscores_in_names") {
    assert(lex(" _this_is_a_valid_name ") == "(Name '_this_is_a_valid_name') (EOF)")
  }

  test("test_special_symbols_in_names") {
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

  test("test_braces") {
    assert(lex("{}") == "(OpenBrace) (CloseBrace) (EOF)")
  }

  test("test_unary_operators") {
    assert(lex("!") == "(UnaryOperator '!') (EOF)")
    assert(lex("!!") == "(UnaryOperator '!') (UnaryOperator '!') (EOF)")
  }

  test("test_binary_operators") {
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

  test("test_method_resolves") {
    assert(lex("a::b") == "(Name 'a') (DoubleColon) (Name 'b') (EOF)")
  }
}
