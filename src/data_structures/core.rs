use std::rc::Rc;
use std::cell::RefCell;
use std::collections::HashMap;

use ::data_structures::ast;
use ::interpreter::{Interpreter};

pub type Shared<T> = Rc<RefCell<T>>;

pub fn make_shared<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

#[derive(Debug, Clone)]
pub enum Type {
    None,
    Bool,
    Int,
    Float,
    Interface(Shared<Interface>)
}

#[derive(Debug)]
pub struct Interface {
    pub methods: Vec<MethodSignature>
}

#[derive(Debug, Clone)]
pub enum Value {
    None,
    Bool(bool),
    Int(i64),
    Float(f64),
    Object(Shared<Object>),

    // Used to support partial evaluation
    Unknown
}

#[derive(Debug, Clone)]
pub struct Variable {
    pub name: String,
    pub value: Value,
    // pub kind: Type
}

pub struct Scope {
    pub parent: Option<Box<Scope>>,
    pub variables: HashMap<String, Variable>
}

pub struct Method {
    pub signature: MethodSignature,
    pub implementation: MethodImplementation
}

#[derive(Debug)]
pub struct MethodSignature {
    pub name: String,
    pub params: Vec<MethodParam>,
    pub return_type: Type
}

#[derive(Debug)]
pub struct MethodParam {
    pub name: String,
    pub kind: Type
}

pub enum MethodImplementation {
    Rust(Box<fn(&mut Interpreter, &mut Scope, Vec<Shared<Value>>) -> Value>),
    Photon(ast::Block)
}

pub struct Module {
    pub name: Option<String>,
    pub methods: HashMap<String, Shared<Method>>
}

pub struct Class {
    pub name: Option<String>,
    pub modules: Vec<Shared<Module>>
}

#[derive(Clone)]
pub struct Object {
    pub class: Shared<Class>,
    pub instance_variables: HashMap<String, Variable>
}
