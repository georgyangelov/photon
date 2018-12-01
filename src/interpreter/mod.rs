// mod interpreter;

// pub use self::interpreter::*;

use ::core::ast::*;
use ::core::*;

pub struct InterpreterError {
    pub message: String
}

pub struct Interpreter {

}

impl Interpreter {
    pub fn new() -> Self {
        Interpreter {}
    }

    pub fn eval(&mut self, ast: &AST) -> Result<Value, InterpreterError> {
        Err(InterpreterError { message: String::from("Not implemented") })
    }
}
