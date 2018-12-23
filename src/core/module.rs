use std::collections::HashMap;

use ::core::*;

#[derive(Debug, Clone)]
pub struct Module {
    pub name: String,
    pub functions: HashMap<String, Shared<Function>>,
    pub ancestors: Vec<Shared<Module>>,
    pub supermodules: Vec<Shared<Module>>
}

impl Module {
    pub fn new(name: &str) -> Self {
        Self {
            name: String::from(name),
            functions: HashMap::new(),
            ancestors: Vec::new(),
            supermodules: Vec::new()
        }
    }

    pub fn add_function(&mut self, name: &str, function: Shared<Function>) {
        self.functions.insert(String::from(name), function);
    }

    pub fn include(&mut self, module: Shared<Module>) {
        self.ancestors.insert(0, module);
    }

    pub fn get(&self, name: &str) -> Option<Shared<Function>> {
        self.functions.get(name)
            .map(Clone::clone)
            .or_else( || {
                for module in self.ancestors.iter() {
                    let function = module.borrow().get(name);

                    if let Some(_) = function {
                        return function;
                    }
                }

                None
            })
    }

    pub fn get_static(&self, name: &str) -> Option<Shared<Function>> {
        for module in self.supermodules.iter() {
            let function = module.borrow().get(name);

            if let Some(_) = function {
                return function;
            }
        }

        None
    }

    pub fn def(&mut self, name: &str, params: Vec<&str>, function: RustFunction) {
        let signature = FnSignature {
            name: String::from(name),
            params: params.iter().map( |name| FnParam { name: String::from(*name) } ).collect()
        };

        let implementation = FnImplementation::Rust(Box::new(function));

        self.add_function(name, make_shared(Function {
            signature,
            implementation
        }));
    }
}
