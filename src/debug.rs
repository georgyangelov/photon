use std::fmt;

use types::*;
use lexer::*;
use parser::*;
use compiler::Compiler;

use itertools::Itertools;

pub fn lex(source: &str) -> String {
    lex_result(source).expect("Could not read token")
}

pub fn lex_error(source: &str) -> Error {
    lex_result(source).expect_err("Did not return error")
}

pub fn lex_result(source: &str) -> Result<String, Error> {
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

pub fn parse_all(source: &str) -> Result<Vec<Value>, Error> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer);
    let mut nodes = Vec::new();

    while parser.has_more_tokens()? {
        nodes.push(parser.parse_next()?)
    }

    Ok(nodes)
}

pub fn eval(source: &str) -> Object {
    let mut compiler = Compiler::new();

    compiler.eval("<testing>", source)
        .map( |value| value.object )
        .unwrap_or_else( |error| panic!(format!("{:?}", error)) )
}

impl fmt::Debug for Value {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        self.object.fmt(f)
    }
}

impl fmt::Debug for Object {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        match self {
            &Object::Bool(value)    => write!(f, "{:?}", value),
            &Object::Int(value)     => write!(f, "{:?}", value),
            &Object::Float(value)   => write!(f, "{:?}", value),
            &Object::Str(ref value) => write!(f, "{:?}", value),

            &Object::NativeValue(ref value) => write!(f, "{:?}", value),

            &Object::Op(Op::NameRef(NameRef { ref name })) => {
                write!(f, "{}", name)?;

                // if let &Some(t) = value_type {
                //     write!(f, ":{:?}", t)?;
                // }

                Ok(())
            },

            &Object::Op(Op::Assign(Assign { ref name, ref value })) => {
                write!(f, "($assign {} {:?})", name, value.object)
            },

            &Object::Struct(Struct { ref values }) => {
                write!(f, "${{")?;

                for (i, (key, value)) in values.iter().enumerate() {
                    if i > 0 {
                        write!(f, ", ")?;
                    }

                    write!(f, "{}: {:?}", key, value.object)?;
                }

                write!(f, "}}")
            },

            &Object::Op(Op::Call(ref method_call)) => method_call.fmt(f),

            &Object::Op(Op::Block(ref block)) => block.fmt(f),

            &Object::Lambda(Lambda { ref params, ref body, .. }) => {
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

            _ => write!(f, "<unknown>")
        }
    }
}

impl fmt::Debug for Param {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(param {})", self.name)
    }
}

impl fmt::Debug for Block {
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

// impl fmt::Debug for ast::FnDef {
//     fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
//         write!(f, "(def {}:{:?}", self.name, self.return_type_expr)?;
//         write!(f, " [")?;
//
//         for (i, param) in self.params.iter().enumerate() {
//             if i > 0 {
//                 write!(f, " ")?;
//             }
//
//             write!(f, "{:?}", param)?;
//         }
//
//         write!(f, "] ")?;
//         write!(f, "{:?}", self.body)?;
//
//         write!(f, ")")
//     }
// }

impl fmt::Debug for Call {
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
