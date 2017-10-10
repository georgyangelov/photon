use std::fmt;

use super::parser::*;
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

pub fn parse_all(source: &str) -> Result<Vec<AST>, ParseError> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer);
    let mut nodes: Vec<AST> = Vec::new();

    while parser.has_more_tokens()? {
        nodes.push(parser.parse_next()?)
    }

    Ok(nodes)
}

impl fmt::Debug for AST {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        match self {
            &AST::IntLiteral { value } => write!(f, "{}", value),
            &AST::FloatLiteral { value } => write!(f, "{}", value),

            &AST::StringLiteral { ref value } => write!(f, "\"{}\"", value),
            &AST::Name { ref name } => write!(f, "{}", name),

            &AST::MethodCall { ref target, ref name, ref args } => {
                write!(f, "({} {:?}", name, target)?;

                for arg in args {
                    write!(f, " {:?}", arg)?;
                }

                write!(f, ")")
            },

            &AST::Block { ref exprs } => {
                write!(f, "{{")?;

                for arg in exprs {
                    write!(f, " {:?}", arg)?;
                }

                write!(f, " }}")
            },

            &AST::Branch { ref condition, ref true_branch, ref false_branch } => {
                write!(f, "(if {:?} {:?}", condition, true_branch)?;

                if let &box AST::Block { ref exprs } = false_branch {
                    if exprs.len() > 0 {
                        write!(f, " {:?}", false_branch)?;
                    }
                } else {
                    write!(f, " {:?}", false_branch)?;
                }

                write!(f, ")")
            },

            &AST::Loop { ref condition, ref body } => write!(f, "(loop {:?} {:?})", condition, body)
        }
    }
}
