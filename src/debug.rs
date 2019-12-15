use std::fmt;
use std::cell::RefCell;

use types::*;
use lexer::*;
use parser::*;
use compiler::Compiler;
use transforms::transform_assignments;

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

pub fn parse(source: &str) -> Result<Value, Error> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer, None);

    parser.parse_next()
}

pub fn parse_all(source: &str) -> Result<Vec<Value>, Error> {
    let mut input = source.as_bytes();
    let lexer = Lexer::new("<testing>", &mut input);
    let mut parser = Parser::new(lexer, None);
    let mut nodes = Vec::new();

    while parser.has_more_tokens()? {
        nodes.push(parser.parse_next()?)
    }

    Ok(nodes)
}

pub fn parse_all_as_block(source: &str) -> Result<Value, Error> {
    let exprs = parse_all(source)?;

    Ok(Value {
        meta: Meta { location: None },
        object: Object::Op(Op::Block(Block { exprs: exprs }))
    })
}

pub fn eval(source: &str) -> Object {
    let mut compiler = Compiler::new();

    compiler.eval("<testing>", source)
        .map( |value| value.object )
        .unwrap_or_else( |error| panic!(format!("{:?}", error)) )
}

pub fn eval_2(source_1: &str, source_2: &str) -> Object {
    let mut compiler = Compiler::new();

    compiler.eval("<testing-1>", source_1)
        .unwrap_or_else( |error| panic!(format!("{:?}", error)) );

    compiler.eval("<testing-2>", source_2)
        .map( |value| value.object )
        .unwrap_or_else( |error| panic!(format!("{:?}", error)) )
}

pub fn assert_transform(actual: &fmt::Debug, expected: &fmt::Debug) {
    let actual_string = format!("{{ {:?} }}", actual);
    let expected_string = format!("{:?}", expected);

    assert_eq!(actual_string, expected_string)
}

pub fn assert_eval(source: &str, expected_source: &str) {
    let actual = eval(source);
    let expected = parse_all_as_block(expected_source)
        .and_then( |value| transform_assignments(value) )
        .unwrap_or_else( |error| panic!(format!("Cannot parse expected source in assert_eval: {:?}", error)));

    assert_transform(&actual, &expected)
}

pub fn assert_eval_2(source_1: &str, source_2: &str, expected_source: &str) {
    let actual = eval_2(source_1, source_2);
    let expected = parse_all_as_block(expected_source)
        .and_then( |value| transform_assignments(value) )
        .unwrap_or_else( |error| panic!(format!("Cannot parse expected source in assert_eval: {:?}", error)));

    assert_transform(&actual, &expected)
}

impl fmt::Debug for Value {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        self.object.fmt(f)
    }
}

impl fmt::Debug for Object {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            &Object::Unknown        => write!(f, "$?"),
            &Object::Bool(value)    => write!(f, "{:?}", value),
            &Object::Int(value)     => write!(f, "{:?}", value),
            &Object::Float(value)   => write!(f, "{:?}", value),
            &Object::Str(ref value) => write!(f, "{:?}", value),

            // &Object::NativeValue(ref value) => write!(f, "{:?}", value),
            &Object::NativeValue(_) => write!(f, "<NativeValue>"),

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

            &Object::Nothing => write!(f, "<nothing>"),
            &Object::NativeLambda(_) => write!(f, "<NativeLambda>")
        }
    }
}

impl fmt::Debug for Param {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "(param {})", self.name)
    }
}

impl fmt::Debug for Block {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
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
//     fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
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
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
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

impl fmt::Debug for Scope {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        if let Some(parent) = &self.parent {
            write!(f, "{:?} -> {:?}", self.vars, parent.borrow())
        } else {
            write!(f, "{:?}", self.vars)
        }
    }
}
