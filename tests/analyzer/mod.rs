use photon::analyzer::constraint_solver::*;

use photon::data_structures::ir::Type;

#[test]
fn test_vars_must_have_type() {
    // Tests `var` => Error

    let mut solver = ConstraintSolver::new();
    let var = solver.new_var();

    let result = solver.solve();

    assert!(result.is_err());
    assert_eq!(AnalyzerError::VarHasNoType(var), result.unwrap_err());
}

#[test]
fn test_simple_assert() {
    // Tests `var:Int`

    let mut solver = ConstraintSolver::new();
    let var = solver.new_var();

    solver.assert(var, Type::Int);

    let result = solver.solve();

    assert!(result.is_ok());
}

#[test]
fn test_conflicting_asserts() {
    // Tests `var:Int`, `var:Bool` => Error

    let mut solver = ConstraintSolver::new();
    let var = solver.new_var();

    solver.assert(var, Type::Int);
    solver.assert(var, Type::Bool);

    let result = solver.solve();

    assert!(result.is_err());
    assert_eq!(AnalyzerError::VarHasNoType(var), result.unwrap_err());
}

#[test]
fn test_assert_intersection_inference() {
    // Tests `var:Int,Bool`, `var:Float,Bool` => `var:Bool`

    let mut solver = ConstraintSolver::new();
    let var = solver.new_var();

    solver.assert_multi(var, vec![Type::Int, Type::Bool]);
    solver.assert_multi(var, vec![Type::Float, Type::Bool]);

    let result = solver.solve();

    assert_result_type(result, var, Type::Bool);
}

#[test]
fn test_assignment_inference() {
    // Tests `to = from:Int` => `to:Int`

    let mut solver = ConstraintSolver::new();
    let from = solver.new_var();
    let to = solver.new_var();

    solver.assert(from, Type::Int);
    solver.assign(to, from);

    let result = solver.solve();

    assert_result_type(result, to, Type::Int);
}

#[test]
fn test_transitive_assignment_inference() {
    // Tests `a = b = c:Int` => `a:Int`, `b:Int`

    let mut solver = ConstraintSolver::new();
    let a = solver.new_var();
    let b = solver.new_var();
    let c = solver.new_var();

    solver.assert(c, Type::Int);
    solver.assign(a, b);
    solver.assign(b, c);

    let result = solver.solve();

    assert_result_type(result, a, Type::Int);
}

#[test]
fn test_constraint_loop() {
    // Tests `a = b:Int`, `b = a` => `a:Int` and no infinite looping :)

    let mut solver = ConstraintSolver::new();
    let a = solver.new_var();
    let b = solver.new_var();

    solver.assert(b, Type::Int);
    solver.assign(a, b);
    solver.assign(b, a);

    let result = solver.solve();

    assert_result_type(result, a, Type::Int);
}

#[test]
fn test_assignment_back_propagation() {
    // Tests `to:Int,Bool = from:Int`

    let mut solver = ConstraintSolver::new();
    let from = solver.new_var();
    let to = solver.new_var();

    solver.assert_multi(from, vec![Type::Int, Type::Bool]);
    solver.assert(to, Type::Int);
    solver.assign(to, from);

    let result = solver.solve();

    assert_result_type(result, from, Type::Int);
}

fn assert_result_type(result: Result<Solution, AnalyzerError>, var: Var, t: Type) {
    assert!(result.is_ok());

    let types = result.unwrap().types;
    let var_type = *types.get(&var).unwrap();

    assert_eq!(t, var_type);
}
