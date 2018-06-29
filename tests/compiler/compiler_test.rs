use photon::testing::*;

#[test]
fn test_simple_functions() {
    assert_eq!(42, call_0::<i64>("def main: Int\n 42 end"));
    assert_eq!(11, call_0::<i64>("def main: Int\n 11 end"));
}

#[test]
fn test_native_operators() {
    assert_eq!(2, call_0::<i64>("def main: Int\n 1 + 1 end"));
    assert_eq!(2, call_0::<i64>("def main: Int\n 3 - 1 end"));
    assert_eq!(4, call_0::<i64>("def main: Int\n 2 * 2 end"));
    assert_eq!(4, call_0::<i64>("def main: Int\n 8 / 2 end"));

    // assert_eq!(2.0, call_0::<f64>("def main: Float\n 1.0 + 1.0 end"));
    // assert_eq!(2.0, call_0::<f64>("def main: Float\n 3.0 - 1.0 end"));
    // assert_eq!(4.0, call_0::<f64>("def main: Float\n 2.0 * 2.0 end"));
    // assert_eq!(4.5, call_0::<f64>("def main: Float\n 9.0 / 2.0 end"));
}

// #[test]
// fn test_method_arguments() {
//     assert_eq!(2, call_2::<i64, i64, i64>("def main(a: Int, b: Int): Int\n a + b end", 1, 1));
// }
