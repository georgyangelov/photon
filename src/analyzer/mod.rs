use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;

use std::iter::FromIterator;

use std::rc::Rc;
use std::borrow::Borrow;

use super::ir::Type;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
enum Constraint {
    Assert { var: Var, types: PossibleTypes },
    Assignment { from: Var, to: Var }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Var(i32);

pub struct Analyzer {
    // TODO: Is this vec needed? We can use `next_var_id` and construct Vars ourselves
    vars: Vec<Var>,
    constraints: Vec<Rc<Constraint>>,

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

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
enum PossibleTypes {
    All,
    Some(Vec<Type>)
}

impl PossibleTypes {
    fn only(t: Type) -> Self {
        PossibleTypes::Some(vec![t])
    }

    fn intersect(&self, other: &PossibleTypes) -> PossibleTypes {
        match self {
            &PossibleTypes::All => other.clone(),
            &PossibleTypes::Some(ref self_types) => {
                match other {
                    &PossibleTypes::All => self.clone(),
                    &PossibleTypes::Some(ref other_types) => {
                        let self_type_set: HashSet<Type> = HashSet::from_iter(self_types.iter().cloned());
                        let other_type_set: HashSet<Type> = HashSet::from_iter(other_types.iter().cloned());

                        PossibleTypes::Some(self_type_set.intersection(&other_type_set).cloned().collect())
                    }
                }
            }
        }
    }

    fn equals(&self, other: &PossibleTypes) -> bool {
        match self {
            &PossibleTypes::All => {
                match other {
                    &PossibleTypes::All => true,
                    _ => false
                }
            },
            &PossibleTypes::Some(ref self_types) => {
                match other {
                    &PossibleTypes::All => false,
                    &PossibleTypes::Some(ref other_types) => {
                        let self_type_set: HashSet<Type> = HashSet::from_iter(self_types.iter().cloned());
                        let other_type_set: HashSet<Type> = HashSet::from_iter(other_types.iter().cloned());

                        self_type_set == other_type_set
                    }
                }
            }
        }
    }
}

#[derive(Debug)]
struct SolutionDraft {
    possible_types: HashMap<Var, PossibleTypes>,

    var_constraints: HashMap<Var, Vec<Rc<Constraint>>>,

    next: VecDeque<Rc<Constraint>>,
    to_be_revalidated: HashSet<Rc<Constraint>>
}

impl SolutionDraft {
    pub fn new(vars: &Vec<Var>, constraints: &Vec<Rc<Constraint>>) -> Self {
        let mut result = Self {
            possible_types: Self::build_initial_possible_types(vars),

            var_constraints: HashMap::new(),

            next: VecDeque::new(),
            to_be_revalidated: HashSet::new()
        };

        result.build_constraints(vars, constraints);
        result.build_initial_next(constraints);

        result
    }

    pub fn possible_types_for(&self, var: Var) -> &PossibleTypes {
        self.possible_types.get(&var)
            .expect("possible_types must include all variables")
    }

    pub fn revalidate_var(&mut self, var: Var) {
        let constraints = self.var_constraints.get(&var)
            .expect("var_constraints must include all vars")
            .clone();

        for constraint in &constraints {
            self.revalidate_constraint(constraint);
        }
    }

    pub fn revalidate_constraint(&mut self, constraint: &Rc<Constraint>) {
        if self.to_be_revalidated.contains(constraint) {
            return;
        }

        self.next.push_back(constraint.clone());
        self.to_be_revalidated.insert(constraint.clone());
    }

    fn build_initial_possible_types(vars: &Vec<Var>) -> HashMap<Var, PossibleTypes> {
        let mut result = HashMap::new();

        for var in vars.iter().cloned() {
            result.insert(var, PossibleTypes::All);
        }

        result
    }

    fn build_constraints(&mut self, vars: &Vec<Var>, constraints: &Vec<Rc<Constraint>>) {
        for var in vars.iter().cloned() {
            self.var_constraints.insert(var, Vec::new());
        }

        for constraint in constraints {
            match constraint.borrow() {
                &Constraint::Assert { var, .. } => self.var_constraints.get_mut(&var)
                    .expect("var_constraints must contain all vars")
                    .push(constraint.clone()),

                &Constraint::Assignment { to, from } => {
                    self.var_constraints.get_mut(&to)
                        .expect("var_constraints must contain all vars")
                        .push(constraint.clone());

                    self.var_constraints.get_mut(&from)
                        .expect("var_constraints must contain all vars")
                        .push(constraint.clone());
                }
            };
        }
    }

    fn build_initial_next(&mut self, constraints: &Vec<Rc<Constraint>>) {
        for constraint in constraints {
            match constraint.borrow() {
                &Constraint::Assert { .. } => self.revalidate_constraint(constraint),
                _ => ()
            };
        }
    }
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
        let constraint = Rc::new(Constraint::Assert {
            var,
            types: PossibleTypes::only(t)
        });

        self.constraints.push(constraint);
    }

    pub fn assert_multi(&mut self, var: Var, types: Vec<Type>) {
        let constraint = Rc::new(Constraint::Assert {
            var,
            types: PossibleTypes::Some(types.iter().cloned().collect())
        });

        self.constraints.push(constraint);
    }

    pub fn assign(&mut self, to: Var, from: Var) {
        let constraint = Rc::new(Constraint::Assignment {
            from,
            to
        });

        self.constraints.push(constraint);
    }

    pub fn solve(&self) -> Result<Solution, AnalyzerError> {
        let mut draft = SolutionDraft::new(&self.vars, &self.constraints);

        while let Some(constraint) = draft.next.pop_front() {
            Self::apply_constraint(&mut draft, &constraint);

            draft.to_be_revalidated.remove(&constraint);
        }

        self.present_solution(&mut draft)
    }

    // TODO: Ability to fail early
    fn apply_constraint(draft: &mut SolutionDraft, constraint: &Rc<Constraint>) {
        match constraint.borrow() {
            &Constraint::Assert { var, ref types } => {
                let mut types_changed;
                let mut next_possible;

                {
                    let currently_possible = draft.possible_types_for(var);

                    next_possible = currently_possible.intersect(types);
                    types_changed = !currently_possible.equals(&next_possible);
                }

                if types_changed {
                    // TODO: Make this a method?
                    draft.possible_types.insert(var, next_possible);
                    draft.revalidate_var(var);
                }
            },

            &Constraint::Assignment { to, from } => {
                // TODO: Fail fast and with appropriate error
                let current_from_types = draft.possible_types_for(from).clone();
                let current_to_types = draft.possible_types_for(to).clone();

                let next_to_types = current_to_types.intersect(&current_from_types);
                let next_from_types = next_to_types.clone();

                if !current_to_types.equals(&next_to_types) {
                    draft.possible_types.insert(to, next_to_types);
                    draft.revalidate_var(to);
                }

                if !current_from_types.equals(&next_from_types) {
                    draft.possible_types.insert(from, next_from_types);
                    draft.revalidate_var(from);
                }
            }
        }
    }

    fn present_solution(&self, draft: &SolutionDraft) -> Result<Solution, AnalyzerError> {
        let mut solution = Solution {
            types: HashMap::new()
        };

        for var in self.vars.iter().cloned() {
            let possible_types = draft.possible_types_for(var);

            match possible_types {
                &PossibleTypes::All => return Err(AnalyzerError::VarHasNoType(var)),

                &PossibleTypes::Some(ref types) => {
                    if types.len() == 0 {
                        return Err(AnalyzerError::VarHasNoType(var));
                    } else if types.len() > 1 {
                        return Err(AnalyzerError::VarMayHaveManyTypes(var, types.iter().cloned().collect()));
                    } else {
                        solution.types.insert(var, types.iter().cloned().next().unwrap());
                    }
                }
            };
        }

        Ok(solution)
    }
}
