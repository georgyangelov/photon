use std::fmt;
use std::rc::Rc;
use std::cell::RefCell;
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct Location {
    pub file_name: Option<String>,
    pub start_line: u32,
    pub start_column: u32,
    pub end_line: u32,
    pub end_column: u32
}

impl Location {
    pub fn extend_with(&self, location: &Location) -> Location {
        Location {
            file_name: self.file_name.clone(),

            start_line: self.start_line,
            start_column: self.start_column,

            end_line: location.end_line,
            end_column: location.end_column
        }
    }
}

#[derive(Clone)]
pub struct Meta {
    pub location: Option<Location>
}

#[derive(Clone)]
pub enum Op {
    Assign(Assign),
    Block(Block),
    Call(Call),
    NameRef(NameRef),
}

#[derive(Clone)]
pub enum Object {
    Unknown,
    Nothing,

    Bool(bool),
    Int(i64),
    Float(f64),
    Str(String),

    Struct(Struct),
    Lambda(Lambda),

    NativeValue(Rc<NativeValue>),
    NativeLambda(NativeLambda),

    Op(Op)
}

#[derive(Clone)]
pub struct Value {
    pub object: Object,
    pub meta: Meta
}

#[derive(Debug, Clone)]
pub enum Error {
    LexError   { message: String, location: Location },
    ParseError { message: String, location: Location },
    ExecError  { message: String, location: Option<Location> }
}

// Operations

#[derive(Clone)]
pub struct Assign {
    pub name: String,
    pub value: Box<Value>
}

#[derive(Clone)]
pub struct Block {
    pub exprs: Vec<Value>
}

#[derive(Clone)]
pub struct NameRef {
    pub name: String
}

#[derive(Clone)]
pub struct Call {
    pub target: Box<Value>,
    pub name: String,
    pub args: Vec<Value>,
    pub may_be_var_call: bool,
    pub module_resolve: bool
}

// Objects

#[derive(Clone)]
pub struct Struct {
    pub values: HashMap<String, Value>
}

#[derive(Clone)]
pub struct Lambda {
    pub params: Vec<Param>,
    pub body: Block,
    pub scope: Option<Shared<Scope>>
}

#[derive(Clone)]
pub struct Param {
    pub name: String
}

pub trait NativeValue {
    fn call(&self, _name: &str, _args: &[Value], location: Option<Location>) -> Result<Value, Error> {
        Err(Error::ExecError {
            message: String::from("Cannot call methods on this native struct"),
            location: location.clone()
        })
    }

    fn to_object(self) -> Object;
}

pub type RustFunction = fn(&[Value]) -> Result<Value, Error>;

#[derive(Clone)]
pub struct NativeLambda {
    pub function: Box<RustFunction>
}

impl From<bool> for Object {
    fn from(value: bool) -> Self {
        Object::Bool(value)
    }
}

impl From<i64> for Object {
    fn from(value: i64) -> Self {
        Object::Int(value)
    }
}

impl From<f64> for Object {
    fn from(value: f64) -> Self {
        Object::Float(value)
    }
}

impl From<String> for Object {
    fn from(value: String) -> Self {
        Object::Str(value)
    }
}

impl From<&str> for Object {
    fn from(value: &str) -> Self {
        Object::Str(String::from(value))
    }
}

pub type Shared<T> = Rc<RefCell<T>>;

pub fn share<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

#[derive(Clone)]
pub struct Macro {
    pub name: String,
    pub handler: Lambda
}

pub struct Scope {
    // TODO: This doesn't need to be RefCell now that it's immutable
    pub parent: Option<Shared<Scope>>,
    pub vars: HashMap<String, Value>
}

impl Scope {
    pub fn new_root() -> Self {
        Scope {
            parent: None,
            vars: HashMap::new()
        }
    }

    pub fn new(parent: Shared<Scope>, vars: HashMap<String, Value>) -> Self {
        Scope {
            parent: Some(parent),
            vars
        }
    }

    pub fn get(&self, name: &str) -> Option<Value> {
        self.vars.get(name)
            .map(Clone::clone)
            .or_else( || self.parent.as_ref().and_then( |parent| parent.borrow().get(name) ) )
    }
}
