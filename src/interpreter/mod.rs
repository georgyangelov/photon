// mod interpreter;

// pub use self::interpreter::*;

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

            &AST::Name { ref name } => self.eval_name(name, scope),

            &AST::TypeHint(TypeHint { ref expr, .. }) => self.eval_ast(expr, scope),
            &AST::Assignment(ref assignment) => self.eval_assignment(assignment, scope),

            &AST::Branch(ref branch) => self.eval_if(branch, scope),
            &AST::Block(ref block) => self.eval_block(block, scope),

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
        let scope = scope.borrow();

        scope.get(name)
            .map( |var| var.value.clone() )
            .ok_or(InterpreterError { message: format!("Cannot find name {}", name) })
    }
}
