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
            &AST::NilLiteral => write!(f, "nil"),
            &AST::BoolLiteral { value } => write!(f, "{:?}", value),
            &AST::IntLiteral { value } => write!(f, "{}", value),
            &AST::FloatLiteral { value } => write!(f, "{}", value),

            &AST::StringLiteral { ref value } => write!(f, "\"{}\"", value),
            &AST::Name { ref name } => write!(f, "{}", name),

            &AST::Assignment { ref name, ref expr } => write!(f, "(= {} {:?})", name, expr),

            &AST::MethodCall { ref target, ref name, ref args, .. } => {
                write!(f, "({} {:?}", name, target)?;

                for arg in args {
                    write!(f, " {:?}", arg)?;
                }

                write!(f, ")")
            },

            &AST::Block { ref exprs, ref catches } => {
                write!(f, "{{")?;

                for expr in exprs {
                    write!(f, " {:?}", expr)?;
                }

                for catch in catches {
                    write!(f, " {:?}", catch)?;
                }

                write!(f, " }}")
            },

            &AST::Lambda { ref params, ref body } => {
                write!(f, "(lambda [")?;

                for (i, param) in params.iter().enumerate() {
                    if i > 0 {
                        write!(f, " ")?;
                    }

                    write!(f, "{:?}", param)?;
                }

                write!(f, "] ")?;
                write!(f, "{:?}", body)?;

                write!(f, ")")
            },

            &AST::Branch { ref condition, ref true_branch, ref false_branch } => {
                write!(f, "(if {:?} {:?}", condition, true_branch)?;

                if let &box AST::Block { ref exprs, .. } = false_branch {
                    if exprs.len() > 0 {
                        write!(f, " {:?}", false_branch)?;
                    }
                } else {
                    write!(f, " {:?}", false_branch)?;
                }

                write!(f, ")")
            },

            &AST::Loop { ref condition, ref body } => write!(f, "(loop {:?} {:?})", condition, body),

            &AST::MethodDef { ref name, ref params, ref return_kind, ref body } => {
                write!(f, "(def {}", name)?;

                if let &Some(ref kind) = return_kind {
                    write!(f, ":{}", kind)?;
                }

                write!(f, " [")?;

                for (i, param) in params.iter().enumerate() {
                    if i > 0 {
                        write!(f, " ")?;
                    }

                    write!(f, "{:?}", param)?;
                }

                write!(f, "] ")?;
                write!(f, "{:?}", body)?;

                write!(f, ")")
            }
        }
    }
}

impl fmt::Debug for MethodParam {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(param {}:{})", self.name, self.kind)
    }
}

impl fmt::Debug for Catch {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(catch ")?;

        if let Some(ref name) = self.name {
            write!(f, "{}:", name)?;
        }

        write!(f, "{} {:?})", self.kind, self.body)
    }
}
