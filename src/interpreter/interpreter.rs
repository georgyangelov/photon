use ::data_structures::ast;
use ::data_structures::core::*;

#[derive(Debug)]
pub struct InterpreterError {
    pub message: String
}

pub struct Interpreter {

}

impl Interpreter {
    pub fn new() -> Self {
        Self {}
    }

    pub fn compile(&mut self, asts: Vec<ast::AST>) -> Result<(), InterpreterError> {
        Ok(())
    }

    pub fn run(&mut self, ast: ast::AST) -> Result<Value, InterpreterError> {
        Ok(Value::None)
    }
}
