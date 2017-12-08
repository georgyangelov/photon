use std::fmt;

use super::parser::*;
use super::compiler::*;

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

pub fn call_0<R>(source: &str) -> R {
    let compiler = Compiler::new();
    let method = compile_method(&compiler, source);
    let jit = jit::JIT::new(&compiler);

    unsafe { jit.call_0::<R>(&method) }
}

pub fn call_1<T1, R>(source: &str, a1: T1) -> R {
    let compiler = Compiler::new();
    let method = compile_method(&compiler, source);
    let jit = jit::JIT::new(&compiler);

    unsafe { jit.call_1::<T1, R>(&method, a1) }
}

pub fn call_2<T1, T2, R>(source: &str, a1: T1, a2: T2) -> R {
    let compiler = Compiler::new();
    let method = compile_method(&compiler, source);
    let jit = jit::JIT::new(&compiler);

    unsafe { jit.call_2::<T1, T2, R>(&method, a1, a2) }
}

pub fn call_3<T1, T2, T3, R>(source: &str, a1: T1, a2: T2, a3: T3) -> R {
    let compiler = Compiler::new();
    let method = compile_method(&compiler, source);
    let jit = jit::JIT::new(&compiler);

    unsafe { jit.call_3::<T1, T2, T3, R>(&method, a1, a2, a3) }
}

fn compile_method<'a>(compiler: &'a Compiler, source: &str) -> CompiledMethod<'a> {
    let nodes = parse_all(source).unwrap();
    let last_node = nodes.last().unwrap();

    if let &AST::MethodDef(ref method_ast) = last_node {
        let compiled_method = compiler.compile_method(method_ast);

        if let Err(reason) = compiler.verify_module() {
            panic!(format!("Module is not valid: {}", reason));
        }

        compiled_method
    } else {
        panic!(format!("The last node should be a method definition. Was {:?}", last_node));
    }
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

            &AST::MethodCall(ref method_call) => method_call.fmt(f),

            &AST::Block(ref block) => block.fmt(f),

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

                if false_branch.exprs.len() > 0 {
                    write!(f, " {:?}", false_branch)?;
                }

                write!(f, ")")
            },

            &AST::Loop { ref condition, ref body } => write!(f, "(loop {:?} {:?})", condition, body),

            &AST::MethodDef(ref method) => method.fmt(f)
        }
    }
}

impl fmt::Debug for MethodParam {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(param {}:{:?})", self.name, self.kind)
    }
}

impl fmt::Debug for Catch {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(catch ")?;

        if let Some(ref name) = self.name {
            write!(f, "{}:", name)?;
        }

        write!(f, "{:?} {:?})", self.kind, self.body)
    }
}

impl fmt::Debug for BlockAST {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{{")?;

        for ref expr in &self.exprs {
            write!(f, " {:?}", expr)?;
        }

        for ref catch in &self.catches {
            write!(f, " {:?}", catch)?;
        }

        write!(f, " }}")
    }
}

impl fmt::Debug for MethodDefAST {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "(def {}:{:?}", self.name, self.return_kind)?;
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

impl fmt::Debug for MethodCallAST {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "({} {:?}", self.name, self.target)?;

        for arg in &self.args {
            write!(f, " {:?}", arg)?;
        }

        write!(f, ")")
    }
}
