use std::collections::HashMap;

use ::core::ast::*;
use ::core::*;

mod core_lib;

use self::core_lib::CoreLib;

pub struct InterpreterError {
    pub message: String
}

pub struct Interpreter {
    core: CoreLib
}

impl Interpreter {
    pub fn new() -> Self {
        let core = CoreLib::new();

        Interpreter { core }
    }

    pub fn eval(&self, ast: &AST) -> Result<Value, InterpreterError> {
        let scope = self.core.root_scope.clone();

        self.eval_ast(ast, scope)
    }

    fn eval_ast(&self, ast: &AST, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        match ast {
            &AST::BoolLiteral   { value } => Ok(Value::Bool(value)),
            &AST::IntLiteral    { value } => Ok(Value::Int(value)),
            &AST::FloatLiteral  { value } => Ok(Value::Float(value)),
            &AST::StringLiteral { ref value } => Ok(Value::String(value.clone())),
            &AST::StructLiteral(ref struct_literal) => self.eval_struct_literal(struct_literal, scope),

            &AST::Name { ref name } => self.eval_name(name, scope),

            &AST::TypeHint(TypeHint { ref expr, .. }) => self.eval_ast(expr, scope),
            &AST::Assignment(ref assignment) => self.eval_assignment(assignment, scope),

            &AST::Branch(ref branch) => self.eval_if(branch, scope),
            &AST::Block(ref block) => self.eval_block(block, scope),

            &AST::ModuleDef(ref def) => self.eval_module_def(def, scope),
            &AST::FnDef(ref def) => self.eval_fn_def(def, scope),
            &AST::FnCall(ref fn_call) => self.eval_fn_call(fn_call, scope),

            _ => Err(InterpreterError { message: String::from("Not implemented") })
        }
    }

    fn eval_if(&self, branch: &Branch, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        match self.eval_ast(&branch.condition, scope.clone())? {
            Value::Bool(true)  => self.eval_block(&branch.true_branch, scope),
            Value::Bool(false) => self.eval_block(&branch.false_branch, scope),

            _ => Err(InterpreterError { message: String::from("Condition must be boolean") })
        }
    }

    fn eval_block(&self, block: &Block, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let mut last_expr_value = Value::None;
        let child_scope = Scope::new_child_scope(scope);

        for expr in &block.exprs {
            last_expr_value = self.eval_ast(&expr, child_scope.clone())?;
        }

        Ok(last_expr_value)
    }

    fn eval_assignment(&self, assign: &Assignment, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        if let AST::Name { ref name } = *assign.name {
            let value = self.eval_ast(&assign.expr, scope.clone())?;

            {
                let mut scope = scope.borrow_mut();

                if scope.has_in_current_scope(name) {
                    return Err(InterpreterError { message: String::from("Cannot reassign variables") });
                }

                scope.assign(Variable { name: name.clone(), value: value.clone() });
            }

            Ok(value)
        } else {
            Err(InterpreterError { message: String::from("Cannot make assignment without name on the left side") })
        }
    }

    fn eval_name(&self, name: &str, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        // TODO: This may be a method called implicitly on `self`, if there is no variable with this name, try a function call
        self.find_name_in_scope(name, scope)
    }

    fn eval_struct_literal(&self, struct_literal: &StructLiteral, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let mut values = HashMap::new();
        let mut struct_module = Module::new("<anonymous>");
        struct_module.include(self.core.struct_module.clone());

        values.insert(String::from("__module__"), Value::Module(make_shared(struct_module)));

        for (name, value_ast) in &struct_literal.tuples {
            values.insert(name.clone(), self.eval_ast(&value_ast, scope.clone())?);
        }

        Ok(Value::Struct(make_shared(Struct { values })))
    }

    fn eval_fn_def(&self, def: &FnDef, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let module = self.find_name_in_scope("SelfModule", scope.clone())?;
        let module = match module {
            Value::Module(ref module) => module.clone(),

            _ => return Err(InterpreterError { message: format!("Cannot define '{}' because it is not in a module", &def.name) })
        };

        let function = self.create_fn(def, scope)?;

        module.borrow_mut().add_function(&def.name, function.clone());

        Ok(Value::Function(function))
    }

    fn create_fn(&self, def: &FnDef, scope: Shared<Scope>) -> Result<Shared<Function>, InterpreterError> {
        let signature = FnSignature {
            name: def.name.clone(),
            params: def.params.iter()
                .map( |param| FnParam { name: param.name.clone() } )
                .collect()
        };

        let implementation = FnImplementation::Photon {
            // TODO: Remove this as it will create loops that cannot be garbage-collected
            scope: scope.clone(),
            body: def.body.clone()
        };

        Ok(make_shared(Function { signature, implementation }))
    }

    fn eval_module_def(&self, def: &ModuleDef, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let def_scope = Scope::new_child_scope(scope.clone());
        let module = make_shared(Module::new(&def.name));

        {
            def_scope.borrow_mut().assign(Variable {
                name: String::from("SelfModule"),
                value: Value::Module(module.clone())
            });

            self.eval_block(&def.body, def_scope)?;
        }

        let module_value = Value::Module(module);

        scope.borrow_mut().assign(Variable {
            name: def.name.clone(),
            value: module_value.clone()
        });

        Ok(module_value)
    }

    fn find_name_in_scope(&self, name: &str, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let scope = scope.borrow();

        scope.get(name)
            .map( |var| var.value.clone() )
            .ok_or(InterpreterError { message: format!("Cannot find name {}", name) })
    }

    fn find_name_in_struct(&self, name: &str, object: Shared<Struct>) -> Option<Value> {
        let object = object.borrow();

        object.values.get(name)
            .map( |value| value.clone() )
    }

    fn eval_fn_call(&self, fn_call: &FnCall, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let target = self.eval_ast(&fn_call.target, scope.clone())?;
        let has_self_arg;

        let function = match target {
            Value::Struct(ref object) => {
                has_self_arg = true;

                let function = self.find_name_in_struct("__module__", object.clone())
                    .and_then( |module| {
                        let module = match module {
                            Value::Module(ref module) => module.clone(),
                            _ => return None
                        };

                        let module = module.borrow();
                        module.get(&fn_call.name)
                    });

                match function {
                    Some(ref function) => function.clone(),
                    None => {
                        let value = self.find_name_in_struct(&fn_call.name, object.clone());

                        match value {
                            Some(ref value) => return Ok(value.clone()),
                            None => return Err(InterpreterError { message: format!("No property or method '{}' present on {:?}", &fn_call.name, target) })
                        }
                    }
                }
            },

            // TODO: This is wrong, this should be the handler for the `::` syntax, not the `.` syntax
            Value::Module(ref module) => {
                has_self_arg = false;

                module.borrow().get(&fn_call.name)
                    .ok_or_else( || InterpreterError { message: format!("No function '{}' present in module '{}'", &fn_call.name, &module.borrow().name) } )?
            },

            Value::String(_) => {
                has_self_arg = true;

                self.core.string_module.borrow().get(&fn_call.name)
                    .ok_or_else(|| error(format!("No method '{}' on String", fn_call.name)))?
            },

            // TODO: Support methods on base values (int, string, etc.)
            _ => return Err(InterpreterError { message: format!("Cannot call methods on {:?}", target) })
        };

        let mut args = Vec::new();

        if has_self_arg {
            args.push(target);
        }

        for ast in &fn_call.args {
            args.push(self.eval_ast(ast, scope.clone())?);
        }

        self.call_fn(function, &args)
    }

    fn call_fn(&self, function: Shared<Function>, args: &[Value]) -> Result<Value, InterpreterError> {
        let function = function.borrow();
        let closure_scope = match function.implementation {
            FnImplementation::Rust   { .. } => Scope::new(),
            FnImplementation::Photon { ref scope, .. } => scope.clone()
        };

        let call_scope = Scope::new_child_scope(closure_scope);
        let fn_params = &function.signature.params;

        if fn_params.len() != args.len() {
            return Err(InterpreterError { message: format!("Argument error. Expected: {}, got {}", fn_params.len(), args.len()) });
        }

        for i in 0..fn_params.len() {
            let name = fn_params[i].name.clone();
            let value = args[i].clone();

            call_scope.borrow_mut().assign(Variable { name, value });
        }

        match function.implementation {
            FnImplementation::Rust(ref body) => (*body)(self, call_scope, args),
            FnImplementation::Photon { ref body, .. }  => self.eval_block(body, call_scope)
        }
    }

    fn find_fn_on_module(&self, scope: &Scope, module_name: &str, fn_name: &str)
            -> Result<Shared<Function>, InterpreterError> {
        let shared_module = scope.get(module_name)
            .map( |var| var.value )
            .and_then( |value| value.expect_module() )
            .ok_or_else(|| error(format!("Cannot find module '{}'", module_name)))?;

        let module = shared_module.borrow();

        module.get(fn_name)
            .ok_or_else(|| error(format!("Cannot find method '{}' in module '{}'", fn_name, module_name)))
    }

    fn call_fn_on_module(&self, scope: &Scope, module_name: &str, fn_name: &str, args: &[Value])
            -> Result<Value, InterpreterError> {
        let function = self.find_fn_on_module(scope, module_name, fn_name)?;

        self.call_fn(function, args)
    }
}

fn error(message: String) -> InterpreterError {
    InterpreterError { message }
}
