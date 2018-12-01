use std::collections::HashMap;
use std::collections::hash_map::Entry;

use ::data_structures::ast;
use ::data_structures::core::*;

#[derive(Debug)]
pub struct InterpreterError {
    pub message: String
}

pub struct Interpreter {
    pub global_module: Shared<Module>,
    pub root_scope: Shared<Scope>
}

impl Interpreter {
    pub fn new() -> Self {
        let global_module = make_shared(Module {
            name: Some(String::from("GlobalMethods")),
            methods: HashMap::new()
        });

        Self {
            global_module,
            root_scope: make_shared(build_root_scope(global_module))
        }
    }

    pub fn compile(&mut self, asts: Vec<&mut ast::AST>) -> Result<(), InterpreterError> {
        for ast in asts {
            self.evaluate_top_level(ast, self.root_scope)?;
        }

        Ok(())
    }

    pub fn run(&mut self, expr: &mut ast::AST) -> Result<Value, InterpreterError> {
        let value = match expr {
            ast::AST::NilLiteral => Value::None,
            ast::AST::BoolLiteral { value } => Value::Bool(*value),
            ast::AST::IntLiteral { value } => Value::Int(*value),
            ast::AST::FloatLiteral { value } => Value::Float(*value),

            // TODO

            _ => return Err(InterpreterError {
                message: format!("Unsupported expression: {:?}", expr)
            })
        };

        Ok(value)
    }

    fn evaluate_top_level(&mut self, expr: &mut ast::AST, scope: Shared<Scope>) -> Result<(), InterpreterError> {
        match expr {
            ast::AST::MethodDef(ref method_def) => {
                let method = Method {

                };

                let self_object = scope.borrow().get_self_object(scope);

                self_object.
            }
        }
    }


}

fn build_root_scope(global_module: Shared<Module>) -> Scope {
    let mut scope = Scope::new(None);

    let global_class = make_shared(Class {
        name: Some(String::from("Global")),
        modules: vec![global_module]
    });

    let global = make_instance(global_class);

    scope.add_variable(Variable {
        name: String::from("self"),
        value: Value::Object(global)
    });

    scope
}

fn make_instance(class: Shared<Class>) -> Shared<Object> {
    make_shared(Object { class, instance_variables: HashMap::new() })
}

impl Scope {
    fn new(parent: Option<Box<Scope>>) -> Self {
        Scope {
            parent,
            variables: HashMap::new()
        }
    }

    fn add_variable(&mut self, var: Variable) {
        self.variables.insert(var.name.clone(), var);
    }

    fn find_variable(&mut self, name: &str) -> Option<&mut Variable> {
        let entry = self.variables.entry(String::from(name));

        match entry {
            Entry::Occupied(entry) => Some(entry.get_mut()),
            Entry::Vacant(_) => self.parent.and_then( |parent| parent.find_variable(name) )
        }
    }

    fn get_self_object(&self) -> Result<Shared<Object>, InterpreterError> {
        let self_var = self.find_variable("self").expect("Cannot find implicit self");

        match self_var.value {
            Value::Object(object) => Ok(object),

            _ => Err(InterpreterError {
                message: String::from("Self is not an object")
            })
        }
    }
}

impl Class {
    fn find_method(&self, name: &str) -> Option<Shared<Method>> {
        for shared_module in self.modules {
            let module = shared_module.borrow();

            if let Some(method) = module.methods.get(name) {
                return Some(method.clone());
            }
        }

        return None;
    }
}
