use photon::debug::*;
use photon::types::*;
use photon::unparser::{unparse as actual_unparse};
use photon::transforms::transform_all_assignments;

#[test]
fn test_no_transform() {
    assert_eq!("true\nfalse", unparse("true; false"));
    assert_eq!("1\n2\n3", unparse("1; 2; 3"));
    assert_eq!("1\n{ |a| a.+(1) }.$call(2)", unparse("1; { |a| a + 1 }(2)"));
}

#[test]
fn test_simple_transform() {
    assert_eq!("{ |answer| answer }.$call(42)", unparse("answer = 42; answer"));
    assert_eq!("{ |num| num.+(1) }.$call(42)", unparse("num = 42; num + 1"));
    assert_eq!("1\n2\n{ |num|\nnum.+(1)\nfalse\n}.$call(42)", unparse("1; 2; num = 42; num + 1; false"));
}

#[test]
fn test_no_code_after_assignment() {
    assert_eq!("42", unparse("num = 42"));
}

#[test]
fn test_multiple_assignments() {
    assert_eq!("{ |num1|
true
{ |num2|
false
{ |num3|
true
num1.+(num2).+(num3)
}.$call(3)
}.$call(2)
}.$call(1)",
        unparse("num1 = 1; true; num2 = 2; false; num3 = 3; true; num1 + num2 + num3")
    );
}

#[test]
fn test_mutliple_consecutive_assignments() {
    assert_eq!(
        "{ |num1| { |num2| { |num3| num1.+(num2).+(num3) }.$call(3) }.$call(num1) }.$call(1)",
        unparse("num1 = 1; num2 = num1; num3 = 3; num1 + num2 + num3")
    );
}

#[test]
fn test_assignments_in_lambdas() {
    assert_eq!("{ |a| { |n| n.+(1) }.$call(2) }", unparse("{ |a| n = 2; n + 1 }"));
}

fn parse(source: &str) -> Vec<Value> {
    parse_all(source).expect("Could not parse")
}

fn unparse(source: &str) -> String {
    let code = parse(source);
    let code = transform_all_assignments(code).expect("Could not transform");

    actual_unparse(&code.iter().collect::<Vec<_>>())
}
