use ::core::*;
use im::vector::Vector;

#[derive(Debug, Clone)]
pub enum Value {
    Bool(bool),
    Int(i64),
    Float(f64),
    String(String),

    Function(Shared<Function>),
    Struct(Shared<Struct>),
    Module(Shared<Module>),
    Array(Vector<Value>)
}

impl Value {
    pub fn expect_bool(&self) -> Option<bool> {
        match self {
            Value::Bool(result) => Some(*result),
            _ => None
        }
    }

    pub fn expect_int(&self) -> Option<i64> {
        match self {
            Value::Int(result) => Some(*result),
            _ => None
        }
    }

    pub fn expect_float(&self) -> Option<f64> {
        match self {
            Value::Float(result) => Some(*result),
            _ => None
        }
    }

    pub fn expect_string(&self) -> Option<String> {
        match self {
            Value::String(string) => Some(string.clone()),
            _ => None
        }
    }

    pub fn expect_function(&self) -> Option<Shared<Function>> {
        match self {
            Value::Function(result) => Some(result.clone()),
            _ => None
        }
    }

    pub fn expect_struct(&self) -> Option<Shared<Struct>> {
        match self {
            Value::Struct(result) => Some(result.clone()),
            _ => None
        }
    }

    pub fn expect_module(&self) -> Option<Shared<Module>> {
        match self {
            Value::Module(result) => Some(result.clone()),
            _ => None
        }
    }

    pub fn expect_array(&self) -> Option<Vector<Value>> {
        match self {
            Value::Array(result) => Some(result.clone()),
            _ => None
        }
    }
}
