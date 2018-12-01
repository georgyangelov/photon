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
    assert_match!(Value::Int(42), run("def answer 42 end; answer()"));
    assert_match!(Value::Int(42), run("def answer 42 end; answer"));
    assert_match!(Value::Int(42), run("def answer(a: _) a end; answer(42)"));
    assert_match!(Value::Int(42), run("def answer(a: _) a end; answer 42"));
}

// #[test]
// fn test_simple_method_calls() {
//     assert_match!(
//         Value::Int(42),
//         run(
//             r#"
//                 def main
//                   42
//                 end
//             "#,
//             "main"
//         )
//     );
// }
