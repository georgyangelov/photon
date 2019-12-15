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
    assert_matches!(eval("1 + 40 + 1"), Object::Int(42));
}

#[test]
fn test_assignment() {
    assert_matches!(eval("answer = 42; answer"), Object::Int(42));
}

#[test]
fn test_lambdas() {
    assert_matches!(eval("{ 42 }"), Object::Lambda(Lambda { params, body, .. }) => {
        assert_eq!(params.len(), 0);
        assert_matches!(body, Block { exprs } => {
            assert_eq!(exprs.len(), 1);
            assert_matches!(exprs[0].object, Object::Int(42));
        });
    });
}

#[test]
fn test_lambda_calls_without_parameters() {
    assert_matches!(eval("{ 42 }.$call"), Object::Int(42));
    assert_matches!(eval("{ 42 }()"), Object::Int(42));
}

#[test]
fn test_lambda_calls_with_parameters() {
    assert_matches!(eval("{ |a| a + 41 }.$call 1"), Object::Int(42));
    assert_matches!(eval("{ |a| a + 41 }(1)"), Object::Int(42));
}

#[test]
fn test_closures() {
    assert_matches!(eval("{ |a| { |b| a + b } }(1)(41)"), Object::Int(42));
}

#[test]
fn test_passing_lambda_as_parameter() {
    assert_eval(
        "{ |fn| fn(1) }({ |a| a + 41 })",
        "42"
    );

    assert_eval(
        "{ |fn| fn(1) }({ |a| b = a; b + 41 })",
        "42"
    );
}

#[test]
fn test_partial_evaluation_with_unknowns() {
    assert_eval(
        "{ |fn| fn(1) + fn(2) + $? }({ |a| a + 1 })",
        "5 + $?"
    );

    assert_eval(
        "fn = { |a| a + 1 }; fn(1) + fn(2) + $?",
        "5 + $?"
    );

    assert_eval(
        "inc = { |a| a + 1 }; a = 3; b = a + 1; inc(a) + inc(b) + $?",
        "9 + $?"
    );

    assert_eval(
        "{ |a| { |b| { |c| a + b + c }(2) }($?) }(1)",
        "{ |b| 1 + b + 2 }($?)"
    );

    assert_eval(
        "a = 1; b = $?; c = 2; a + b + c",
        "b = $?; 1 + b + 2"
    );
}

// #[test]
// fn test_partial_eval_through_unknown_call() {
//     assert_eval(
//         "{ |a| { |b| a + b } }($?)(1)",
//         "{ |a| a + 1 }($?)"
//     );
// }

// #[test]
// fn test_partial_eval_through_unknown_call_2() {
//     assert_eval(
//         "{ |a| { |b| { |c| a + b + c } } }($?)($?)(1)",
//         "{ |a| { |b| a + b + 1 } }($?)($?)"
//     );
// }

#[test]
fn test_partial_evaluation_inside_nonevaluated_code() {
    assert_eval(
        "{ |a| 1 + 2 + a }",
        "{ |a| 3 + a }"
    );

    assert_eval(
        "{ |unknown| { |fn| fn(1) + fn(2) + unknown }({ |a| a + 1 }) }",
        "{ |unknown| 5 + unknown }"
    );

    assert_eval(
        "{ |a| { |c| { |b| a + b + c }($?) }(2) }(1)",
        "{ |b| 1 + b + 2 }($?)"
    );
}

// #[test]
// fn test_partial_evaluation_with_some_arguments() {
//     assert_eval(
//         "{ |a, b| 1 + a + b }(1, $?)",
//         "{ |b| 2 + b }($?)"
//     );
//
//     assert_eval(
//         "{ |a, b, c| a + b + c }(1, $?, 2)",
//         "{ |b| 1 + b + 2 }($?)"
//     );
// }

// #[test]
// fn test_partial_eval_with_unusable_knowns() {
//     assert_eval(
//         "{ |a| $? + a }(1)",
//         "$? + 1"
//     );
// }

#[test]
fn test_simple_macros() {
    assert_eval_2(
        "Core.define_macro('add_one', { |parser| e = parser.read_expr(); e + 1 })",
        "add_one $?",

        "$? + 1"
    );
}

#[test]
fn test_calling_methods_on_core() {
    assert_eval(
        "Core.fourty_two",
        "42"
    );
}
