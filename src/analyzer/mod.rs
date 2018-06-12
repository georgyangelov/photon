use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;

use super::ir::Type;

#[derive(Debug, Clone)]
enum Constraint {
    Assert { var: Var, types: PossibleTypes },
    // VarAssign { from: Var, to: Var },
    // ValAssign { var: Var, t: Type }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Var(i32);

pub struct Analyzer {
    // TODO: Is this vec needed? We can use `next_var_id` and construct Vars ourselves
    vars: Vec<Var>,
    constraints: Vec<Constraint>,

    next_var_id: i32
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AnalyzerError {
    VarHasNoType(Var),
    VarMayHaveManyTypes(Var, HashSet<Type>)
}

#[derive(Debug, Clone)]
pub struct Solution {
    pub types: HashMap<Var, Type>
}

#[derive(Debug, Clone)]
enum PossibleTypes {
    All,
    Some(HashSet<Type>)
}

impl PossibleTypes {
    fn only(t: Type) -> Self {
        let mut set = HashSet::new();
        set.insert(t);

        PossibleTypes::Some(set)
    }

    fn intersect(&self, other: &PossibleTypes) -> PossibleTypes {
        match self {
            &PossibleTypes::All => other.clone(),
            &PossibleTypes::Some(ref self_types) => {
                match other {
                    &PossibleTypes::All => self.clone(),
                    &PossibleTypes::Some(ref other_types) => PossibleTypes::Some(
                        self_types.intersection(other_types).cloned().collect()
                    )
                }
            }
        }
    }
}

#[derive(Debug)]
struct SolutionDraft {
    possible_types: HashMap<Var, PossibleTypes>,
    var_constraints: HashMap<Var, Vec<Constraint>>,
    next: VecDeque<Var>
}

impl Analyzer {
    pub fn new() -> Self {
        Self {
            vars: Vec::new(),
            constraints: Vec::new(),
            next_var_id: 1
        }
    }

    pub fn new_var(&mut self) -> Var {
        let var = Var(self.next_var_id);

        self.next_var_id += 1;
        self.vars.push(var);

        var
    }

    pub fn assert(&mut self, var: Var, t: Type) {
        self.constraints.push(Constraint::Assert {
            var,
            types: PossibleTypes::only(t)
        });
    }

    pub fn assert_multi(&mut self, var: Var, types: Vec<Type>) {
        self.constraints.push(Constraint::Assert {
            var,
            types: PossibleTypes::Some(types.iter().cloned().collect())
        })
    }

    pub fn solve(&self) -> Result<Solution, AnalyzerError> {
        let mut draft = SolutionDraft {
            possible_types: self.build_initial_possible_types(),
            var_constraints: self.build_var_constraints(),
            next: VecDeque::new()
        };

        for constraint in &self.constraints {
            match constraint {
                &Constraint::Assert { var, .. } => draft.next.push_back(var)
            };
        }

        while let Some(var) = draft.next.pop_front() {
            let constraints = draft.var_constraints.get(&var)
                .expect("var_constraints must contain all vars")
                // TODO: Figure out a way to not clone this
                .clone();

            for constraint in constraints {
                Self::apply_constraint(&mut draft, &constraint);
            }
        }

        self.present_solution(&mut draft)
    }

    fn build_initial_possible_types(&self) -> HashMap<Var, PossibleTypes> {
        let mut result = HashMap::new();

        for var in self.vars.iter().cloned() {
            result.insert(var, PossibleTypes::All);
        }

        result
    }

    fn build_var_constraints(&self) -> HashMap<Var, Vec<Constraint>> {
        let mut result = HashMap::new();

        for var in self.vars.iter().cloned() {
            result.insert(var, Vec::new());
        }

        for constraint in &self.constraints {
            match constraint {
                &Constraint::Assert { var, .. } => result.get_mut(&var)
                    .expect("var_constraints must contain all vars")
                    .push(constraint.clone())
            }
        }

        result
    }

    // TODO: Ability to fail early
    fn apply_constraint(draft: &mut SolutionDraft, constraint: &Constraint) {
        match constraint {
            &Constraint::Assert { var, ref types } => {
                let next_possible = {
                    let currently_possible = draft.possible_types.get(&var)
                        .expect("possible_types must contain all vars");

                    currently_possible.intersect(types)
                };

                draft.possible_types.insert(var, next_possible);
            }
        }
    }

    fn present_solution(&self, draft: &SolutionDraft) -> Result<Solution, AnalyzerError> {
        let mut solution = Solution {
            types: HashMap::new()
        };

        for var in self.vars.iter().cloned() {
            let possible_types = draft.possible_types.get(&var)
                .expect("possible_types must contain all vars");

            match possible_types {
                &PossibleTypes::All => return Err(AnalyzerError::VarHasNoType(var)),

                &PossibleTypes::Some(ref types) => {
                    if types.len() == 0 {
                        return Err(AnalyzerError::VarHasNoType(var));
                    } else if types.len() > 1 {
                        return Err(AnalyzerError::VarMayHaveManyTypes(var, types.clone()));
                    } else {
                        solution.types.insert(var, types.iter().cloned().next().unwrap());
                    }
                }
            };
        }

        Ok(solution)
    }
}
