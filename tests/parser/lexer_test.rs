use photon::testing::*;

#[test]
fn test_eof() {
    assert_eq!("(EOF)", lex(""));
}

#[test]
fn test_skip_whitespace() {
    assert_eq!("(Number '12') (NewLine) (EOF)", lex("   \t 12 \n "));
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
        "(OpenParen) (CloseParen) (OpenBracket) (CloseBracket) (Comma) (Pipe) \
         (Dot) (Colon) (Number '12345') (String 'test') (NewLine) (Name 'doend') (EOF)",
        lex("()[],|.:12345\"test\"\ndoend")
    );
}

#[test]
fn test_single_quote_strings() {
    assert_eq!("(String 'test') (EOF)", lex("'test'"));
    assert_eq!("(String 'te\\ns\\t') (EOF)", lex("'te\\ns\\t'"));
    assert_eq!("(String 'te'st\\') (EOF)", lex("'te\\'st\\\\'"));
}

#[test]
fn test_numbers() {
    assert_eq!("(Number '1234') (EOF)", lex("1234"));
    assert_eq!("(Number '1234.5678') (EOF)", lex("1234.5678"));
}

#[test]
fn test_literals() {
    assert_eq!("(Nil) (EOF)", lex("nil"));
    assert_eq!("(Bool 'true') (EOF)", lex("true"));
    assert_eq!("(Bool 'false') (EOF)", lex("false"));
}

#[test]
fn test_calls_on_literals() {
    assert_eq!("(Number '42.2') (Dot) (Name 'abs') (EOF)", lex("42.2.abs"));
    assert_eq!("(Number '42') (Dot) (Name 'abs') (EOF)", lex("42.abs"));
    assert_eq!(
        "(String 'test') (Dot) (Name 'length') (Number '3') (EOF)",
        lex("\"test\".length 3")
    );
}

#[test]
fn test_keywords() {
    assert_eq!(
        "(Begin) (Do) (End) (If) (Elsif) (Else) (While) (Def) (Catch) (EOF)",
        lex("begin do end if elsif else while def catch")
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
fn test_special_symbols_in_names() {
    assert_eq!("(Name '@an_instance_variable') (EOF)", lex("@an_instance_variable"));
    assert_eq!("(Name 'valid_name?') (Name 'valid_name!') (EOF)", lex("valid_name? valid_name!"));

    assert_eq!(
        "(Name 'invalid_nam!') (Name 'e') (Name 'invalid_n?') (Name 'ame') (EOF)",
        lex("invalid_nam!e invalid_n?ame")
    );
}

#[test]
fn test_error_on_unknown_token() {
    lex_error("test %");
}

#[test]
fn test_braces() {
    assert_eq!("(OpenBrace) (CloseBrace) (EOF)", lex("{}"));
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
