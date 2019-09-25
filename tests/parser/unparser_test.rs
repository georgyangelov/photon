use photon::debug::*;
use photon::types::*;
use photon::unparser::{unparse as actual_unparse};

#[test]
fn test_constants() {
    assert_eq!("true", unparse("true"));
    assert_eq!("false", unparse("false"));
    assert_eq!("42", unparse("42"));
    assert_eq!("42.2", unparse("42.2"));
    assert_eq!("true\nfalse", unparse("true; false"));
}

#[test]
fn test_operators() {
    assert_eq!("1.+(2)", unparse("1 + 2"));
    assert_eq!("1.-(2)", unparse("1 - 2"));
    assert_eq!("1.*(2)", unparse("1 * 2"));
    assert_eq!("1./(2)", unparse("1 / 2"));

    assert_eq!("true.!()", unparse("!true"));
    assert_eq!("true.!().!().!()", unparse("!!!true"));
}

#[test]
fn test_calls() {
    assert_eq!("1.abs()", unparse("1.abs"));
    assert_eq!("1.max(1, 2, 3)", unparse("1.max 1, 2, 3"));
}

#[test]
fn test_lambdas() {
    assert_eq!("{ |a| a.+(1) }", unparse("{ |a| a + 1 }"));
    assert_eq!("{ |a| a.+(1) }.$call(41)", unparse("{ |a| a + 1 }(41)"));

    // TODO: Indentation :)
    assert_eq!("{ |a|\na.+(1)\n42\n}", unparse("{ |a| a + 1; 42 }"));
}

fn parse(source: &str) -> Vec<Value> {
    parse_all(source).expect("Could not parse")
}

fn unparse(source: &str) -> String {
    let code = parse(source);

    actual_unparse(&code.iter().collect::<Vec<_>>())
}
