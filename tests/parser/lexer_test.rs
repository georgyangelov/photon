use photon::testing::*;

#[test]
fn test_eof() {
    assert_eq!("(EOF)", lex(""));
}

#[test]
fn test_skip_whitespace() {
    assert_eq!("(Number '12') (NewLine '\n') (EOF)", lex("   \t 12 \n "));
}

#[test]
fn test_inline_comments() {
    assert_eq!("(EOF)", lex("# test"));
    assert_eq!("(EOF)", lex("# # # # # test"));
    assert_eq!("(Number '1234') (EOF)", lex("# test \n 1234"));
}

#[test]
fn test_simple_tokens() {
    assert_eq!(
        "(OpenParen '(') (CloseParen ')') (OpenBracket '[') (CloseBracket ']') (Comma ',') \
         (Dot '.') (Colon ':') (Number '12345') (String 'test') (NewLine '\n') (Name 'doend') (EOF)",
        lex("()[],.:12345\"test\"\ndoend")
    );
}

#[test]
fn test_numbers() {
    assert_eq!("(Number '1234') (EOF)", lex("1234"));
    assert_eq!("(Number '1234.5678') (EOF)", lex("1234.5678"));
}

#[test]
fn test_calls_on_literals() {
    assert_eq!("(Number '42.2') (Dot '.') (Name 'abs') (EOF)", lex("42.2.abs"));
    assert_eq!(
        "(String 'test') (Dot '.') (Name 'length') (Number '3') (EOF)",
        lex("\"test\".length 3")
    );
}

#[test]
fn test_keywords() {
    assert_eq!(
        "(Do 'do') (End 'end') (If 'if') (Elsif 'elsif') (Else 'else') (While 'while') \
         (Def 'def') (EOF)",
        lex("do end if elsif else while def")
    );
}

#[test]
fn test_escape_sequences() {
    assert_eq!("(String 'a\nb\\c\td') (EOF)", lex("\"a\\nb\\\\c\\td\""));
}

#[test]
fn test_unicode() {
    assert_eq!("(Name 'тест') (String 'тест') (EOF)", lex("тест \"тест\""));
}

#[test]
fn test_underscores_in_names() {
    assert_eq!("(Name '_this_is_a_valid_name') (EOF)", lex(" _this_is_a_valid_name "));
}

#[test]
fn test_error_on_unknown_token() {
    lex_error("test %");
}

#[test]
fn test_braces() {
    assert_eq!("(OpenBrace '{') (CloseBrace '}') (EOF)", lex("{}"));
}

#[test]
fn test_unary_operators() {
    assert_eq!("(UnaryOperator '!') (EOF)", lex("!"));
    assert_eq!("(UnaryOperator '!') (UnaryOperator '!') (EOF)", lex("!!"));
}

#[test]
fn test_binary_operators() {
    assert_eq!("(Name 'a') (BinaryOperator '==') (Name 'b') (EOF)", lex("a== b"));
    assert_eq!("(BinaryOperator '+') (BinaryOperator '-') (EOF)", lex("+ -"));
    assert_eq!("(BinaryOperator 'and') (BinaryOperator 'or') (EOF)", lex("and or"));
    assert_eq!("(BinaryOperator '*') (BinaryOperator '/') (EOF)", lex("* /"));
    assert_eq!("(BinaryOperator '<') (BinaryOperator '>') (EOF)", lex("< >"));
    assert_eq!("(BinaryOperator '<=') (BinaryOperator '>=') (BinaryOperator '!=') (EOF)", lex("<= >= !="));
    assert_eq!("(BinaryOperator '+=') (BinaryOperator '-=') (EOF)", lex("+= -="));
    assert_eq!("(BinaryOperator '/=') (BinaryOperator '*=') (EOF)", lex("/= *="));
    assert_eq!("(Name 'a') (BinaryOperator '=') (Name 'b') (EOF)", lex("a = b"));
}
