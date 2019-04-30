use std::rc::Rc;
use std::cell::RefCell;
use std::fmt;
use std::collections::HashMap;

use ::core::{ast, Scope, Value};
use ::interpreter::{Interpreter, InterpreterError};

pub type Shared<T> = Rc<RefCell<T>>;
pub type RustFunction = fn(&Interpreter, Shared<Scope>, &[Value]) -> Result<Value, InterpreterError>;

pub fn make_shared<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

#[derive(Debug, Clone)]
pub struct Struct {
    pub values: HashMap<String, Value>
}

#[derive(Debug, Clone)]
pub struct Variable {
    pub name: String,
    pub value: Value
}

#[derive(Debug)]
pub struct Function {
    pub signature: FnSignature,
    pub implementation: FnImplementation
}

#[derive(Debug)]
pub struct FnSignature {
    pub name: String,
    pub params: Vec<FnParam>
}

#[derive(Debug)]
pub struct FnParam {
    pub name: String
}

pub enum FnImplementation {
    Rust(Box<RustFunction>),

    // TODO: Hold onto only the used variables, do not share the entire world
    Photon { scope: Shared<Scope>, body: ast::Block }
}

impl fmt::Debug for FnImplementation {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            FnImplementation::Rust { .. } => write!(f, "{}", "[native code]"),
            FnImplementation::Photon { ref body, .. } => body.fmt(f)
        }
    }
}
