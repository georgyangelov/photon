use std::collections::HashMap;

use ::interpreter::*;
use ::core::*;

#[derive(Debug, Clone)]
pub struct Module {
    pub name: String,
    pub functions: HashMap<String, Shared<Function>>,
    pub included: Vec<Shared<Module>>
}

impl Module {
    pub fn new(name: &str) -> Self {
        Self {
            name: String::from(name),
            functions: HashMap::new(),
            included: Vec::new()
        }
    }

    pub fn add_function(&mut self, name: &str, function: Shared<Function>) {
        self.functions.insert(String::from(name), function);
    }

    pub fn include(&mut self, module: Shared<Module>) {
        self.included.insert(0, module);
    }

    pub fn get(&self, name: &str) -> Option<Shared<Function>> {
        self.functions.get(name)
            .map(Clone::clone)
            .or_else( || {
                for module in self.included.iter() {
                    let function = module.borrow().get(name);

                    if let Some(_) = function {
                        return function;
                    }
                }

                None
            })
    }
}
