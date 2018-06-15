use photon::analyzer::*;

use photon::ir::Type;

#[test]
fn test_vars_must_have_type() {
    // Tests `var` => Error

    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    let result = analyzer.solve();

    assert!(result.is_err());
    assert_eq!(AnalyzerError::VarHasNoType(var), result.unwrap_err());
}

#[test]
fn test_simple_assert() {
    // Tests `var:Int`

    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert(var, Type::Int);

    let result = analyzer.solve();

    assert!(result.is_ok());
}

#[test]
fn test_conflicting_asserts() {
    // Tests `var:Int`, `var:Bool` => Error

    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert(var, Type::Int);
    analyzer.assert(var, Type::Bool);

    let result = analyzer.solve();

    assert!(result.is_err());
    assert_eq!(AnalyzerError::VarHasNoType(var), result.unwrap_err());
}

#[test]
fn test_assert_intersection_inference() {
    // Tests `var:Int,Bool`, `var:Float,Bool` => `var:Bool`

    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert_multi(var, vec![Type::Int, Type::Bool]);
    analyzer.assert_multi(var, vec![Type::Float, Type::Bool]);

    let result = analyzer.solve();

    assert_result_type(result, var, Type::Bool);
}

#[test]
fn test_assignment_inference() {
    // Tests `to = from:Int` => `to:Int`

    let mut analyzer = Analyzer::new();
    let from = analyzer.new_var();
    let to = analyzer.new_var();

    analyzer.assert(from, Type::Int);
    analyzer.assign(to, from);

    let result = analyzer.solve();

    assert_result_type(result, to, Type::Int);
}

#[test]
fn test_transitive_assignment_inference() {
    // Tests `a = b = c:Int` => `a:Int`, `b:Int`

    let mut analyzer = Analyzer::new();
    let a = analyzer.new_var();
    let b = analyzer.new_var();
    let c = analyzer.new_var();

    analyzer.assert(c, Type::Int);
    analyzer.assign(a, b);
    analyzer.assign(b, c);

    let result = analyzer.solve();

    assert_result_type(result, a, Type::Int);
}

#[test]
fn test_constraint_loop() {
    // Tests `a = b:Int`, `b = a` => `a:Int` and no infinite looping :)

    let mut analyzer = Analyzer::new();
    let a = analyzer.new_var();
    let b = analyzer.new_var();

    analyzer.assert(b, Type::Int);
    analyzer.assign(a, b);
    analyzer.assign(b, a);

    let result = analyzer.solve();

    assert_result_type(result, a, Type::Int);
}

#[test]
fn test_assignment_back_propagation() {
    // Tests `to:Int,Bool = from:Int`

    let mut analyzer = Analyzer::new();
    let from = analyzer.new_var();
    let to = analyzer.new_var();

    analyzer.assert_multi(from, vec![Type::Int, Type::Bool]);
    analyzer.assert(to, Type::Int);
    analyzer.assign(to, from);

    let result = analyzer.solve();

    assert_result_type(result, from, Type::Int);
}

fn assert_result_type(result: Result<Solution, AnalyzerError>, var: Var, t: Type) {
    assert!(result.is_ok());

    let types = result.unwrap().types;
    let var_type = *types.get(&var).unwrap();

    assert_eq!(t, var_type);
}
