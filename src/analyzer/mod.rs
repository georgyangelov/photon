use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;

use super::ir::Type;

#[derive(Debug, Clone)]
enum Constraint {
    Assert { var: Var, t: Type },
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
    VarHasNoType(Var)
}

#[derive(Debug, Clone)]
pub struct Solution {
    types: HashMap<Var, Type>
}

#[derive(Debug, Clone)]
enum PossibleTypes {
    All,
    Some(HashSet<Type>)
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
        self.constraints.push(Constraint::Assert { var, t });
    }

    pub fn solve(&self) -> Result<(), AnalyzerError> {
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

        self.verify_draft(&mut draft)
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
            &Constraint::Assert { var, t } => {
                let next_possible = {
                    let currently_possible = draft.possible_types.get(&var)
                        .expect("possible_types must contain all vars");

                    let mut type_set = HashSet::new();
                    type_set.insert(t);

                    Self::intersect_types(
                        currently_possible,
                        &PossibleTypes::Some(type_set)
                    )
                };

                draft.possible_types.insert(var, next_possible);
            }
        }
    }

    fn intersect_types(a: &PossibleTypes, b: &PossibleTypes) -> PossibleTypes {
        match a {
            &PossibleTypes::All => b.clone(),
            &PossibleTypes::Some(ref a_types) => {
                match b {
                    &PossibleTypes::All => a.clone(),
                    &PossibleTypes::Some(ref b_types) => PossibleTypes::Some(
                        a_types.intersection(b_types).map( |t| *t ).collect()
                    )
                }
            }
        }
    }

    fn verify_draft(&self, draft: &SolutionDraft) -> Result<(), AnalyzerError> {
        for var in self.vars.iter().cloned() {
            let possible_types = draft.possible_types.get(&var)
                .expect("possible_types must contain all vars");

            match possible_types {
                &PossibleTypes::All => return Err(AnalyzerError::VarHasNoType(var)),

                &PossibleTypes::Some(ref types) => {
                    if types.len() != 1 {
                        // TODO: More descriptive error
                        return Err(AnalyzerError::VarHasNoType(var));
                    }
                }
            };
        }

        Ok(())
    }
}
