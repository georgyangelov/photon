use std::collections::HashMap;

use super::Shared;

#[derive(Debug)]
pub struct Runtime {
    pub functions: HashMap<String, Shared<Function>>
}

impl Runtime {
    pub fn new() -> Self {
        Self {
            functions: HashMap::new()
        }
    }

    pub fn add_function(&mut self, function: Shared<Function>) {
        let name = function.borrow().name.clone();

        self.functions.insert(name, function);
    }
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

#[derive(Debug, Clone, Hash, PartialEq)]
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
