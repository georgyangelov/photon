use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;

use super::ir::Type;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
enum Constraint {
    Assert { var: Var, t: Type },
    VarAssign { from: Var, to: Var },
    ValAssign { var: Var, t: Type }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
struct Var(i32)

pub struct Analyzer {
    constraints: HashMap<Var, Vec<Constraint>>,
    next_var_id: i32,

    // Starting points
    asserts: Vec<Var>,
    val_assigns: Vec<Var>
}

impl Analyzer {
    pub fn new() -> Self {
        Self {
            constraints: Vec::new(),
            next_var_id: 0
        }
    }

    pub fn new_var() -> Var {
        let id = Var(next_var_id);

        next_var_id += 1;

        id
    }

    fn add_constraint(&mut self, var: Var, constraint: Constraint) {
        self.constraints.entry(var)
            .or_insert( || Vec::new() )
            .push(constraint);
    }

    pub fn add_assert(&mut self, var: Var, t: Type) {
        self.add_constraint(var, Constraint::Assert { var, t });
        self.asserts.put(var);
    }

    pub fn add_var_assign(&mut self, from: Var, to: Var) {
        self.add_constraint(from, Constraint::VarAssign { from, to });
    }

    pub fn add_val_assign(&mut self, var: Var, t: Type) {
        self.add_constraint(var, Constraint::ValAssign { var, t });
        self.val_assigns.put(var);
    }

    pub fn solve(&self) -> Result<Solution, SolutionError> {
        let mut types = HashMap::new::<Var, Type>();
        let mut next = VecDeque::new::<Var>();
        let mut seen = HashSet::new::<Var>();

        for var in asserts {
            next.push_back(var);
        }

        for var in val_assigns {
            next.push_back(var);
        }

        while let Some(var) = next.pop_front() {
            seen.insert(var);

            if let constraints = self.constraints.get(var) {
                for constraint in constraints {
                    match constraint {
                        Constraint::Assert { t, .. } => {
                            if let Some(current_type) = types.get(var) {
                                if current_type != t {
                                    return Err(SolutionError {
                                        broken_constraint: constraint.clone()
                                    });
                                }
                            } else {
                                types.put(var, t);
                            }
                        },

                        Constraint::ValAssign { t, .. } => {
                            if let Some(current_type) = types.get(var) {
                                if current_type != t {
                                    return Err(SolutionError {
                                        broken_constraint: constraint.clone()
                                    });
                                }
                            } else {
                                types.put(var, t);
                            }
                        },

                        Constraint::VarAssign { to, .. } => {
                            if !seen.contains(to) {
                                next.push_back(to);
                            }
                        }
                    }
                }
            }
        }
    }
}

pub struct Solution {
    types: HashMap<Var, Type>
}

pub struct SolutionError {
    pub broken_constraint: Constraint
}
