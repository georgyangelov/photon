use std::collections::HashMap;

use ::core::*;

pub struct Scope {
    pub parent: Option<Shared<Scope>>,
    pub vars: HashMap<String, Variable>
}

impl Scope {
    pub fn new() -> Shared<Self> {
        make_shared(Scope {
            parent: None,
            vars: HashMap::new()
        })
    }

    pub fn new_child_scope(this: Shared<Scope>) -> Shared<Self> {
        make_shared(Scope {
            parent: Some(this),
            vars: HashMap::new()
        })
    }

    pub fn assign(&mut self, var: Variable) {
        self.vars.insert(var.name.clone(), var);
    }

    pub fn has_in_current_scope(&self, name: &str) -> bool {
        self.vars.contains_key(name)
    }

    pub fn get(&self, name: &str) -> Option<Variable> {
        self.vars.get(name)
            .map(Clone::clone)
            .or_else( || self.parent.as_ref().and_then( |parent| parent.borrow().get(name) ) )
    }
}
