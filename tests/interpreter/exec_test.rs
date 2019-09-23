use photon::debug::*;
use photon::types::*;

#[test]
fn test_literals() {
    assert_matches!(eval("true"), Object::Bool(true));
    assert_matches!(eval("false"), Object::Bool(false));
    assert_matches!(eval("42"), Object::Int(42));
    assert_matches!(eval("42.2"), Object::Float(value) => {
        assert_eq!(value, 42.2);
    });
    assert_matches!(eval("\"test\""), Object::Str(ref str) => {
        assert_eq!(str, "test");
    });
}

#[test]
fn test_basic_int_methods() {
    assert_matches!(eval("1 + 41"), Object::Int(42));
}

#[test]
fn test_lambdas() {
    assert_matches!(eval("{ 42 }"), Object::Lambda(Lambda { params, body }) => {
        assert_eq!(params.len(), 0);
        assert_matches!(body, Block { exprs } => {
            assert_eq!(exprs.len(), 1);
            assert_matches!(exprs[0].object, Object::Int(42));
        });
    });
}

#[test]
fn test_lambda_calls_without_parameters() {
    assert_matches!(eval("{ 42 }.call"), Object::Int(42));
}

#[test]
fn test_lambda_calls_with_parameters() {
    assert_matches!(eval("{ |a| a + 41 }.call 1"), Object::Int(42));
}
