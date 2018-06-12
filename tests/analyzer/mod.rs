use photon::analyzer::*;

use photon::ir::Type;

#[test]
fn test_vars_must_have_type() {
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    let result = analyzer.solve();

    assert!(result.is_err());
    assert_eq!(AnalyzerError::VarHasNoType(var), result.unwrap_err());
}

#[test]
fn test_simple_assert() {
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert(var, Type::Int);

    let result = analyzer.solve();

    assert!(result.is_ok());
}

#[test]
fn test_conflicting_asserts() {
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
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert_multi(var, vec![Type::Int, Type::Bool]);
    analyzer.assert_multi(var, vec![Type::Float, Type::Bool]);

    let result = analyzer.solve();

    assert!(result.is_ok());

    let types = result.unwrap().types;
    let var_type = *types.get(&var).unwrap();

    assert_eq!(Type::Bool, var_type);
}
