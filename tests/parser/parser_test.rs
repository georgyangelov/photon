use photon::parser::*;
use photon::testing::*;
use itertools::Itertools;

#[test]
fn test_number_literals() {
    assert_eq!("12345", parse("12345 "));
    assert_eq!("1234.5678", parse("1234.5678 "));
}

#[test]
fn test_negative_number_literals() {
    assert_eq!("-1234", parse("-1234"));
    assert_eq!("-1234.5", parse("-1234.5"));
}

#[test]
fn test_negating_expressions() {
    assert_eq!("(- test)", parse("-test"));
    assert_eq!("(+ -5 5)", parse("-   5 + 5"));
    assert_eq!("(- (+ 5 5))", parse("-   (5 + 5)"));
}

#[test]
fn test_string_literals() {
    assert_eq!("\"Hello world!\"", parse("\"Hello world!\""));
    assert_eq!("\"\"\n\"", parse("\"\\\"\\n\""));
}

#[test]
fn test_simple_ifs() {
    assert_eq!("(if a { (b self c) })", parse("if a \n b c end"));
    assert_eq!("(if a { b c })", parse("if a \n b\n c end"));
    assert_eq!("(if a { b } { d })", parse("if a \n b else d end"));
    assert_eq!("(if a { b c } { d e })", parse("if a \n b \n c else d \n e end"));
    assert_eq!("(if a { (test b) })", parse("if a \n b.test end"));
}

#[test]
fn test_elsif() {
    assert_eq!("(if 1 { 2 } { (if 3 { 4 }) })", parse("if 1\n 2\nelsif 3\n4\nend"));
    assert_eq!("(if 1 { 2 } { (if 3 { 4 } { 5 }) })", parse("if 1\n 2\nelsif 3\n4\nelse\n5\nend"));
}

#[test]
fn test_loops() {
    assert_eq!("(loop a { b c })", parse("while a\n b\n c end"));
    assert_eq!("(loop a { (b self c) })", parse("while a\n b c end"));
    assert_eq!("(loop a { (c b) })", parse("while a\n b.c end"));
}

#[test]
fn test_infix_operators() {
    assert_eq!("(+ (+ (+ 1 2) 3) 4)", parse("1 + 2 + 3 + 4"));
    assert_eq!("(* (* (* 1 2) 3) 4)", parse("1 * 2 * 3 * 4"));
    assert_eq!("(- (- (- 1 2) 3) 4)", parse("1 - 2 - 3 - 4"));
    assert_eq!("(/ (/ (/ 1 2) 3) 4)", parse("1 / 2 / 3 / 4"));
    assert_eq!("(= a 15)", parse("a = 15"));
}

#[test]
fn test_prefix_operators() {
    assert_eq!("(! a)", parse("!a"));
    assert_eq!("(! (! a))", parse("!!a"));
}

#[test]
fn test_operator_precedence() {
    assert_eq!("(+ 1 (* 2 3))", parse("1 + 2 * 3"));

    assert_eq!("(== 1 (+ 2 (* 2 3)))", parse("1 == 2 + 2 * 3"));
    assert_eq!("(== 1 (+ (* 2 2) 3))", parse("1 == 2 * 2 + 3"));
    assert_eq!("(!= 1 (+ (* 2 2) 3))", parse("1 != 2 * 2 + 3"));
    assert_eq!("(<= 1 (+ (* 2 2) 3))", parse("1 <= 2 * 2 + 3"));
    assert_eq!("(>= 1 (+ (* 2 2) 3))", parse("1 >= 2 * 2 + 3"));
    assert_eq!("(< 1 (+ (* 2 2) 3))", parse("1 < 2 * 2 + 3"));
    assert_eq!("(> 1 (+ (* 2 2) 3))", parse("1 > 2 * 2 + 3"));

    assert_eq!("(+ (- 1 (* (/ 2 3) 4)) 5)", parse("1 - 2 / 3 * 4 + 5"));

    assert_eq!("(= a (* 5 5))", parse("a = 5 * 5"));
}

#[test]
fn test_parens_for_precedence() {
    assert_eq!("(* (+ 1 2) 3)", parse("(1 + 2) * 3"));
}

#[test]
fn test_unary_operator_precedence() {
    assert_eq!("(+ 1 (* 2 (! a)))", parse("1 + 2 * !a"));
    assert_eq!("(+ (! 1) (* 2 a))", parse("!1 + 2 * a"));
}

#[test]
fn test_newlines_in_expressions() {
    assert_eq!("(= a (* 5 5))", parse("a =\n\n 5 * 5"));

    assert_eq!("(- (+ 1 2) 5)", parse("1 + 2 - 5"));
    assert_eq!("(+ 1 2) -5", parse("1 + 2 \n - 5"));

    assert_eq!("(+ 1 (* 2 3))", parse("1 +\n\n 2 * \n\n 3"));

    parse_error("1 +\n\n 2 \n * \n\n 3");
}

#[test]
fn test_names() {
    assert_eq!("test test_two test3", parse("test \n test_two \n test3"));
}

#[test]
fn test_method_calls() {
    assert_eq!("method", parse("method"));
    assert_eq!("(method self)", parse("method()"));
    assert_eq!("(method target)", parse("target.method"));
    assert_eq!("(method target)", parse("target.method()"));
}

#[test]
fn test_method_calls_with_arguments() {
    assert_eq!("(method self a)", parse("method(a)"));
    assert_eq!("(method self a)", parse("method a"));

    assert_eq!("(method self a b c)", parse("method(a, b, c)"));
    assert_eq!("(method self a b c)", parse("method a, b, c"));

    assert_eq!("(method self a b c)", parse("method a, \n\n b,\n c"));

    assert_eq!("(method one a b c) (d two)", parse("one.method a, \n\n b,\n c\n two.d"));

    assert_eq!("(method target a)", parse("target.method(a)"));
    assert_eq!("(method target a)", parse("target.method a"));

    assert_eq!("(method target a b c)", parse("target.method(a, b, c)"));
    assert_eq!("(method target a b c)", parse("target.method a, b, c"));
}

#[test]
fn test_argument_associativity() {
    assert_eq!("(one self (two self a b))", parse("one two a, b"));
    assert_eq!("(one self (two self a) b)", parse("one two(a), b"));
    assert_eq!("(one self (two self a b))", parse("one(two a, b)"));
    assert_eq!("(one self (two self a b) c d)", parse("one two(a, b), c, d"));

    assert_eq!("(method self a b c) d", parse("method a, \n\n b,\n c\n d"));
    assert_eq!("(method self a b c) d", parse("method(a, \n\n b,\n c\n) d"));
    assert_eq!("(method self a b (c self (d self (e self f))))", parse("method a, \n\n b,\n c d e f"));
}

#[test]
fn test_method_call_priority() {
    assert_eq!("(one self (+ a b))", parse("one a + b"));
    assert_eq!("(+ (one self a) b)", parse("one(a) + b"));

    assert_eq!("(one self a (+ b c))", parse("one a, b + c"));
    assert_eq!("(+ (one self a b) c)", parse("one(a, b) + c"));
}

#[test]
fn test_method_call_line_completeness() {
    parse_error("call 1234 b");
    parse_error("call \"test\" b");
}

fn parse(source: &str) -> String {
    parse_all(source).expect("Could not parse").iter()
        .map( |ref ast| format!("{:?}", ast) )
        .join(" ")
}

fn parse_error(source: &str) -> ParseError {
    parse_all(source).expect_err("Did not return error")
}
