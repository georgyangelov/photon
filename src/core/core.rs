use std::rc::Rc;
use std::cell::RefCell;

use ::core::{ast, Scope};
use ::interpreter::{Interpreter};

pub type Shared<T> = Rc<RefCell<T>>;

pub fn make_shared<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

#[derive(Debug, Clone)]
pub enum Value {
    None,
    Bool(bool),
    Int(i64),
    Float(f64),

    // Used to support partial evaluation
    Unknown
}

#[derive(Debug, Clone)]
pub struct Variable {
    pub name: String,
    pub value: Value
}

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
    Rust(Box<fn(&mut Interpreter, &mut Scope, Vec<Shared<Value>>) -> Value>),
    Photon(ast::Block)
}
