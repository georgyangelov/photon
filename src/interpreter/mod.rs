// mod interpreter;

// pub use self::interpreter::*;
use std::collections::HashMap;

use ::core::ast::*;
use ::core::*;

pub struct InterpreterError {
    pub message: String
}

pub struct Interpreter {
    root_scope: Shared<Scope>
}

impl Interpreter {
    pub fn new() -> Self {
        Interpreter {
            root_scope: Scope::new()
        }
    }

    pub fn eval(&mut self, ast: &AST) -> Result<Value, InterpreterError> {
        let scope = self.root_scope.clone();

        self.eval_ast(ast, scope)
    }

    fn eval_ast(&mut self, ast: &AST, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        match ast {
            &AST::BoolLiteral  { value } => Ok(Value::Bool(value)),
            &AST::IntLiteral   { value } => Ok(Value::Int(value)),
            &AST::FloatLiteral { value } => Ok(Value::Float(value)),
            &AST::StructLiteral(ref struct_literal) => self.eval_struct_literal(struct_literal, scope),

            &AST::Name { ref name } => self.eval_name(name, scope),

            &AST::TypeHint(TypeHint { ref expr, .. }) => self.eval_ast(expr, scope),
            &AST::Assignment(ref assignment) => self.eval_assignment(assignment, scope),

            &AST::Branch(ref branch) => self.eval_if(branch, scope),
            &AST::Block(ref block) => self.eval_block(block, scope),

            &AST::FnDef(ref def) => self.eval_fn_def(def, scope),
            &AST::FnCall(ref fn_call) => self.eval_fn_call(fn_call, scope),

            _ => Err(InterpreterError { message: String::from("Not implemented") })
        }
    }

    fn eval_if(&mut self, branch: &Branch, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        match self.eval_ast(&branch.condition, scope.clone())? {
            Value::Bool(true)  => self.eval_block(&branch.true_branch, scope),
            Value::Bool(false) => self.eval_block(&branch.false_branch, scope),

            _ => Err(InterpreterError { message: String::from("Condition must be boolean") })
        }
    }

    fn eval_block(&mut self, block: &Block, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let mut last_expr_value = Value::None;
        let child_scope = Scope::new_child_scope(scope);

        for expr in &block.exprs {
            last_expr_value = self.eval_ast(&expr, child_scope.clone())?;
        }

        Ok(last_expr_value)
    }

    fn eval_assignment(&mut self, assign: &Assignment, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
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

    fn eval_name(&mut self, name: &str, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        // TODO: This may be a method called implicitly on `self`, if there is no variable with this name, try a function call
        self.find_name_in_scope(name, scope)
    }

    fn eval_struct_literal(&mut self, struct_literal: &StructLiteral, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let mut values = HashMap::new();

        for (name, value_ast) in &struct_literal.tuples {
            values.insert(name.clone(), self.eval_ast(&value_ast, scope.clone())?);
        }

        Ok(Value::Struct(make_shared(Struct { values })))
    }

    fn eval_fn_def(&mut self, def: &FnDef, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let function = self.create_fn(def, scope.clone())?;
        let function_value = Value::Function(function);
        let mut scope = scope.borrow_mut();

        scope.assign(Variable { name: def.name.clone(), value: function_value.clone() });

        Ok(function_value)
    }

    fn create_fn(&self, def: &FnDef, scope: Shared<Scope>) -> Result<Shared<Function>, InterpreterError> {
        let signature = FnSignature {
            name: def.name.clone(),
            params: def.params.iter()
                .map( |param| FnParam { name: param.name.clone() } )
                .collect()
        };

        let implementation = FnImplementation::Photon {
            scope: scope.clone(),
            body: def.body.clone()
        };

        Ok(make_shared(Function { signature, implementation }))
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

    fn eval_fn_call(&mut self, fn_call: &FnCall, scope: Shared<Scope>) -> Result<Value, InterpreterError> {
        let target = self.eval_ast(&fn_call.target, scope)?;
        let function = match target {
            Value::Struct(ref object) => self.find_name_in_struct(&fn_call.name, object.clone()),

            // TODO: Support methods on base values (int, string, etc.)
            _ => return Err(InterpreterError { message: format!("Cannot call methods on {:?}", target) })
        };

        match function {
            Some(ref value) => Ok(value.clone()),
            None => Err(InterpreterError { message: format!("No method '{}' present on {:?}", &fn_call.name, target) })
        }

        // let mut args = Vec::new();
        //
        // for ast in &fn_call.args {
        //     args.push(self.eval_ast(ast, scope.clone())?);
        // }
        //
        // self.call_fn(function, &args)
    }

    fn call_fn(&mut self, function: Shared<Function>, args: &[Value]) -> Result<Value, InterpreterError> {
        let function = function.borrow();
        let closure_scope = match function.implementation {
            FnImplementation::Rust   { ref scope, .. } => scope,
            FnImplementation::Photon { ref scope, .. } => scope
        }.clone();

        let call_scope = Scope::new_child_scope(closure_scope);
        let fn_params = &function.signature.params;

        if fn_params.len() != args.len() {
            return Err(InterpreterError { message: String::from("Missing or extra arguments") });
        }

        for i in 0..fn_params.len() {
            let name = fn_params[i].name.clone();
            let value = args[i].clone();

            call_scope.borrow_mut().assign(Variable { name, value });
        }

        let result = match function.implementation {
            FnImplementation::Rust { ref body, .. } => (*body)(self, call_scope, args),
            FnImplementation::Photon { ref body, .. }  => self.eval_block(body, call_scope)?
        };

        Ok(result)
    }
}
