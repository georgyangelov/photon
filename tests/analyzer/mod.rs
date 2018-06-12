use photon::analyzer::*;

use photon::ir::Type;

#[test]
fn test_vars_must_have_type() {
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    let result = analyzer.solve();

    assert_eq!(Err(AnalyzerError::VarHasNoType(var)), result);
}

#[test]
fn test_simple_assert() {
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert(var, Type::Int);

    let result = analyzer.solve();

    assert_eq!(Ok(()), result);
}

#[test]
fn test_conflicting_asserts() {
    let mut analyzer = Analyzer::new();
    let var = analyzer.new_var();

    analyzer.assert(var, Type::Int);
    analyzer.assert(var, Type::Bool);

    let result = analyzer.solve();

    assert_eq!(Err(AnalyzerError::VarHasNoType(var)), result);
}
