use photon::testing::*;

#[test]
fn test_simple_functions() {
    assert_eq!(42, call_0::<i64>("def test: Int\n 42 end"));
    assert_eq!(11, call_0::<i64>("def test: Int\n 11 end"));
}
