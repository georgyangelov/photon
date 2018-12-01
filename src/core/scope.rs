use std::collections::HashMap;

use ::core::*;

pub struct Scope {
    pub parent: Option<Shared<Scope>>,
    pub vars: HashMap<String, Variable>
}
