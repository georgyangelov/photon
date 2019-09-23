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
