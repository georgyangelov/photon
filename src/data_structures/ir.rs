use std::collections::HashMap;
use std::fmt;

use super::Shared;

#[derive(Debug)]
pub struct Runtime {
    pub functions: HashMap<String, Shared<Function>>
}

#[derive(Debug)]
pub struct Scope {
    pub runtime: Shared<Runtime>,
    pub vars: HashMap<String, Shared<Variable>>
}

#[derive(Debug)]
pub struct Function {
    pub name: String,
    pub signature: FunctionSignature,
    pub implementation: FunctionImpl
}

#[derive(Debug, Clone)]
pub struct FunctionSignature {
    pub params: Vec<Shared<Variable>>,
    pub return_type: Type
}

#[derive(Debug)]
pub enum FunctionImpl {
    Intrinsic,
    Photon(PhotonFunction)
}

#[derive(Debug)]
pub struct PhotonFunction {
    // pub scope: Shared<Scope>,
    pub code: Block
}

#[derive(Debug, Clone)]
pub struct Variable {
    // pub ast: Option<&AST>,
    pub name: String,
    pub value_type: Type
}

#[derive(Debug)]
pub struct IR {
    // TODO: Figure out a way for this to work with lifetimes
    // pub ast: &AST,
    // pub value_type: Option<Type>,
    pub instruction: Instruction
}

#[derive(Debug)]
pub enum Instruction {
    // Nop,
    Literal(ConstValue),
    VariableRef(VariableRef),
    Call(Call),
    Block(Block)
}

#[derive(Debug)]
pub enum ConstValue {
    Nil,
    Bool(bool),
    Int(i64),
    Float(f64)
}

#[derive(Debug)]
pub struct VariableRef {
    // pub scope: Shared<Scope>,
    pub variable: Shared<Variable>
}

#[derive(Debug)]
pub struct Call {
    pub function: Shared<Function>,
    pub args: Vec<IR>
}

#[derive(Debug)]
pub struct Block {
    pub code: Vec<IR>
}

#[derive(Debug, Clone, Copy, Hash, PartialEq, Eq)]
pub enum Type {
    Nil,
    Bool,
    Int,
    Float
}

// impl fmt::Debug for Type {
//     fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
//         write!(f, "{}", self)
//     }
// }
