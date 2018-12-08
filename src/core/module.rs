use std::collections::HashMap;

use ::core::*;

#[derive(Debug, Clone)]
pub struct Module {
    pub name: String,
    pub functions: HashMap<String, Shared<Function>>
}

impl Module {
    pub fn add_function(&mut self, name: &str, function: Shared<Function>) {
        self.functions.insert(name.into(), function);
    }
}
