use std::rc::Rc;
use std::cell::RefCell;
use std::collections::HashMap;

use ::data_structures::ast;
use ::interpreter::{Interpreter};

type Shared<T> = Rc<RefCell<T>>;

#[derive(Debug)]
pub enum Type {
    None,
    Bool,
    Int,
    Float,
    Interface(Interface)
}

#[derive(Debug)]
pub struct Interface {
    pub methods: Vec<MethodSignature>
}

pub enum Value {
    None,
    Bool(bool),
    Int(i64),
    Float(f64),
    Object(Object)
}

pub struct Scope {
    pub parent: Option<Box<Scope>>,
    pub variables: HashMap<String, Value>
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
    pub methods: HashMap<String, Method>
}

pub struct Class {
    pub modules: Vec<Shared<Module>>
}

pub struct Object {
    pub class: Class
}
