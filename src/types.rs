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

pub struct Meta {
    pub location: Option<Location>
}

pub enum Op {
    Block(Block),
    NameRef(NameRef),
    Call(Call)
}

pub enum Object {
    Nothing,
    Bool(bool),
    Int(i64),
    Float(f64),
    String(String),

    Struct(Struct),
    Lambda(Lambda),

    NativeStruct(NativeStruct),
    NativeLambda(NativeLambda),

    Op(Op)
}

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

pub struct Block {
    pub exprs: Vec<Value>
}

pub struct NameRef {
    pub name: String
}

pub struct Call {
    pub target: Box<Value>,
    pub name: String,
    pub args: Vec<Value>,
    pub may_be_var_call: bool,
    pub module_resolve: bool
}

// Objects

pub struct Struct {
    pub values: HashMap<String, Value>
}

pub struct Lambda {
    pub params: Vec<Param>,
    pub body: Block
}

pub struct Param {
    pub name: String
}

pub struct NativeStruct {
    pub ptr: *mut ()
}

pub type RustFunction = fn(&[Value]) -> Result<Value, Error>;

pub struct NativeLambda {
    pub function: Box<RustFunction>
}
