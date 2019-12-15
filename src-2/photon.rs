use std::fs;

use ::core::*;
use ::parser::*;
use ::interpreter::*;

#[derive(Debug)]
pub struct ParseOrInterpretError {
    pub message: String
}

impl From<ParseError> for ParseOrInterpretError {
    fn from(error: ParseError) -> Self {
        Self { message: error.message }
    }
}

impl From<InterpreterError> for ParseOrInterpretError {
    fn from(error: InterpreterError) -> Self {
        Self { message: error.message }
    }
}

pub struct Photon {
    pub interpreter: Interpreter
}

impl Photon {
    pub fn new() -> Photon {
        Photon {
            interpreter: Interpreter::new()
        }
    }

    pub fn eval(&mut self, file_name: &str, source: &str) -> Result<Value, ParseOrInterpretError> {
        let mut input = source.as_bytes();
        let lexer = Lexer::new(file_name, &mut input);
        let mut parser = Parser::new(lexer, None);

        let mut last_result = None;

        while parser.has_more_tokens()? {
            let token = parser.parse_next()?;

            last_result = Some(self.interpreter.eval(&token)?);
        }

        Ok(last_result.unwrap_or_else(|| self.interpreter.core.none.clone()))
    }

    pub fn load(&mut self, file_name: &str) -> Result<Value, ParseOrInterpretError> {
        // TODO: Better error handling
        let source = fs::read_to_string(file_name)
            .expect(&format!("Cannot read file {}", file_name));

        self.eval(file_name, &source)
    }
}
