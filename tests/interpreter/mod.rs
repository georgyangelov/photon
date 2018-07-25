use photon::data_structures::core::Value;
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
fn test_simple_method_calls() {
    assert_match!(
        Value::Int(42),
        run(
            r#"
                def main
                  42
                end
            "#,
            "main"
        )
    );
}
