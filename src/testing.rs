use std::fmt;

use ::parser::*;
use ::interpreter::*;

use ::core::*;

use itertools::Itertools;

pub fn lex(source: &str) -> String {
    lex_result(source).expect("Could not read token")
}

pub fn lex_error(source: &str) -> ParseError {
    lex_result(source).expect_err("Did not return error")
}

pub fn lex_result(source: &str) -> Result<String, ParseError> {
    let mut input = source.as_bytes();
    let mut lexer = Lexer::new("<testing>", &mut input);
    let mut tokens: Vec<Token> = Vec::new();

    loop {
        let token = lexer.next_token()?;

        if token.token_type == TokenType::EOF {
            tokens.push(token);
            break;
        }

        tokens.push(token);
    }

    let result = tokens.iter()
        .map( |ref t| format!("{}", t) )
        .join(" ");

    Ok(result)
}

pub fn parse_all(source: &str) -> Result<Vec<ast::AST>, ParseError> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer);
    let mut nodes = Vec::new();

    while parser.has_more_tokens()? {
        nodes.push(parser.parse_next()?)
    }

    Ok(nodes)
}

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

pub fn run(source: &str) -> Result<Value, ParseOrInterpretError> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer);
    let interpreter = Interpreter::new();

    let mut last_result = None;

    while parser.has_more_tokens()? {
        let token = parser.parse_next()?;

        last_result = Some(interpreter.eval(&token)?);
    }

    last_result.ok_or(ParseOrInterpretError { message: String::from("Nothing to interpret") })
}

impl fmt::Debug for ast::AST {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        match self {
            // &ast::AST::NilLiteral => write!(f, "nil"),
            &ast::AST::BoolLiteral { value } => write!(f, "{:?}", value),
            &ast::AST::IntLiteral { value } => write!(f, "{}", value),
            &ast::AST::FloatLiteral { value } => write!(f, "{}", value),

            &ast::AST::StringLiteral { ref value } => write!(f, "\"{}\"", value),
            &ast::AST::Name { ref name, .. } => {
                write!(f, "{}", name)?;

                // if let &Some(t) = value_type {
                //     write!(f, ":{:?}", t)?;
                // }

                Ok(())
            },

            &ast::AST::StructLiteral(ref struct_literal) => {
                write!(f, "($ [")?;

                for (i, tuple) in struct_literal.tuples.iter().enumerate() {
                    if i > 0 {
                        write!(f, " ")?;
                    }

                    write!(f, "({}, {:?})", tuple.0, tuple.1)?;
                }

                write!(f, "])")
            },

            &ast::AST::TypeHint(ast::TypeHint { ref expr, ref type_expr, .. }) => {
                write!(f, "{:?}:{:?}", expr, type_expr)
            },

            &ast::AST::Assignment(ast::Assignment { ref name, ref expr, .. }) => {
                write!(f, "(= {:?} {:?})", name, expr)?;

                // if let &Some(t) = value_type {
                //     write!(f, ":{:?}", t)?;
                // }

                Ok(())
            },

            &ast::AST::FnCall(ref method_call) => method_call.fmt(f),

            &ast::AST::Block(ref block) => block.fmt(f),

            // &ast::AST::Lambda { ref params, ref body } => {
            //     write!(f, "(lambda [")?;
            //
            //     for (i, param) in params.iter().enumerate() {
            //         if i > 0 {
            //             write!(f, " ")?;
            //         }
            //
            //         write!(f, "{:?}", param)?;
            //     }
            //
            //     write!(f, "] ")?;
            //     write!(f, "{:?}", body)?;
            //
            //     write!(f, ")")
            // },

            &ast::AST::Branch(ast::Branch { ref condition, ref true_branch, ref false_branch }) => {
                write!(f, "(if {:?} {:?}", condition, true_branch)?;

                if false_branch.exprs.len() > 0 {
                    write!(f, " {:?}", false_branch)?;
                }

                write!(f, ")")
            },

            // &ast::AST::Loop { ref condition, ref body } => write!(f, "(loop {:?} {:?})", condition, body),

            &ast::AST::ModuleDef(ref module) => module.fmt(f),
            &ast::AST::FnDef(ref method) => method.fmt(f),

            &ast::AST::Value(ref value) => value.fmt(f)
        }
    }
}

impl fmt::Debug for ast::UnparsedFnParam {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(param {}:{:?})", self.name, self.type_expr)
    }
}

impl fmt::Debug for ast::Block {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{{")?;

        for ref expr in &self.exprs {
            write!(f, " {:?}", expr)?;
        }

        write!(f, " }}")?;

        // if let Some(t) = self.value_type {
        //     write!(f, ":{:?}", t)?;
        // }

        Ok(())
    }
}

impl fmt::Debug for ast::ModuleDef {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        if let Some(ref name) = self.name {
            write!(f, "(module {} {:?})", name, self.body)
        } else {
            write!(f, "(module {:?})", self.body)
        }
    }
}

impl fmt::Debug for ast::FnDef {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(def {}:{:?}", self.name, self.return_type_expr)?;
        write!(f, " [")?;

        for (i, param) in self.params.iter().enumerate() {
            if i > 0 {
                write!(f, " ")?;
            }

            write!(f, "{:?}", param)?;
        }

        write!(f, "] ")?;
        write!(f, "{:?}", self.body)?;

        write!(f, ")")
    }
}

impl fmt::Debug for ast::FnCall {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        if self.module_resolve {
            write!(f, "({:?}::{}", self.target, self.name)?;
        } else {
            write!(f, "({} {:?}", self.name, self.target)?;
        }

        for arg in &self.args {
            write!(f, " {:?}", arg)?;
        }

        write!(f, ")")?;

        // if let Some(t) = self.value_type {
        //     write!(f, ":{:?}", t)?;
        // }

        Ok(())
    }
}

// impl fmt::Debug for core::Object {
//     fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
//         write!(f, "Object")
//     }
// }
