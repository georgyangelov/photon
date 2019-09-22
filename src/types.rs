use std::fmt;
use std::rc::Rc;
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
    Block(Block),
    NameRef(NameRef),
    Call(Call)
}

#[derive(Clone)]
pub enum Object {
    Unknown,
    Nothing,

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
    pub body: Block
}

#[derive(Clone)]
pub struct Param {
    pub name: String
}

pub trait NativeValue: fmt::Debug {
    fn call(&self, _name: String, _args: &[Value]) -> Result<Value, Error> {
        Err(Error::ExecError {
            message: String::from("Cannot call methods on this native struct"),
            location: None
        })
    }

    fn to_object(self) -> Object;
}

pub type RustFunction = fn(&[Value]) -> Result<Value, Error>;

#[derive(Clone)]
pub struct NativeLambda {
    pub function: Box<RustFunction>
}
