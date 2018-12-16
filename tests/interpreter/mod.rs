use photon::core::{Value};
use photon::testing::*;

macro_rules! assert_match(
    ($expected:pat , $actual:expr) => (
        let actual = $actual;

        if let Err(error) = actual {
            panic!(error.message);
        }

        match actual {
            Ok($expected) => {},
            _ => panic!(format!(
                "assertion failed: no match (expected: {}, given: {:?})",
                stringify!($expected),
                &actual
            ))
        }
    );
);

#[test]
fn test_simple_literals() {
    assert_match!(Value::Bool(true), run("true"));
    assert_match!(Value::Bool(false), run("false"));
    assert_match!(Value::Int(42), run("42"));
}

#[test]
fn test_simple_branches() {
    assert_match!(Value::Int(42), run("if true; 42 else 11 end"));
    assert_match!(Value::Int(11), run("if false; 42 else 11 end"));
    assert_match!(Value::None, run("if false; 42 end"));
}

#[test]
fn test_ignores_type_hints() {
    assert_match!(Value::Int(42), run("42:bullshit_type"));
}

#[test]
fn test_simple_assignment() {
    assert_match!(Value::Int(42), run("a = 42; a"));
}

#[test]
fn test_scope_nesting() {
    assert_match!(Value::Int(42), run("a = 42; begin a = 11 end; a"));
    assert_match!(Value::Int(11), run("a = 42; begin a = 11; a end"));
}

#[test]
fn test_simple_fn_definitions() {
    assert_match!(Value::Int(42), run("module Test; def answer 42 end; end; Test.answer()"));
    assert_match!(Value::Int(42), run("module Test; def answer 42 end; end; Test.answer"));
    assert_match!(Value::Int(42), run("module Test; def answer(a: _) a end; end; Test.answer(42)"));
    assert_match!(Value::Int(42), run("module Test; def answer(a: _) a end; end; Test.answer 42"));
}

#[test]
fn test_struct_literals() {
    assert_match!(Value::Int(42), run("${test: 42}.test"));
    assert_match!(Value::Int(42), run("a = ${test: 42}; a.test"));
}

#[test]
fn test_struct_modules() {
    assert_match!(Value::Int(42), run("module Test; def answer(self: _) 42 end; end; ${}.include(Test).answer"));
}
