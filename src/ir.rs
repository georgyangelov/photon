use std::collections::HashMap;
use std::cell::RefCell;
use std::rc::Rc;

use super::parser::{self, AST};

pub type Shared<T> = Rc<RefCell<T>>;

pub fn make_shared<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

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
    pub params: Vec<Shared<Variable>>,
    pub implementation: FunctionImpl,
    pub return_type: Type
}

#[derive(Debug)]
pub enum FunctionImpl {
    Intrinsic,
    Photon(PhotonFunction)
}

#[derive(Debug)]
pub struct PhotonFunction {
    pub scope: Shared<Scope>,
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
    pub value_type: Option<Type>,
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

#[derive(Debug, Clone, Copy)]
pub enum Type {
    Nil,
    Bool,
    Int,
    Float
}

#[derive(Debug)]
pub struct BuildError {
    message: String
}

impl Runtime {
    pub fn new() -> Shared<Runtime> {
        make_shared(Runtime {
            functions: HashMap::new()
        })
    }

    pub fn add_function(&mut self, function: &Shared<Function>) {
        self.functions.insert(function.borrow().name.clone(), Rc::clone(&function));
    }
}

impl Function {
    pub fn build(ast: &parser::MethodDefAST, runtime: Shared<Runtime>)
            -> Result<Shared<Function>, BuildError> {
        let name = ast.name.clone();
        let mut scope = Scope { runtime, vars: HashMap::new() };
        let mut params = vec![];
        let return_type = value_type_from_ast_kind(ast.return_kind);

        for param in &ast.params {
            let variable = make_shared(Variable {
                name: param.name.clone(),
                value_type: value_type_from_ast_kind(param.kind)
            });

            params.push(Rc::clone(&variable));
            scope.vars.insert(param.name.clone(), Rc::clone(&variable));
        }

        let scope = make_shared(scope);
        let code = IR::build_block(&ast.body, Rc::clone(&scope))?;
        let implementation = FunctionImpl::Photon(PhotonFunction {
            scope: scope,
            code: code
        });

        Ok(make_shared(Function { name, params, return_type, implementation }))
    }
}

fn value_type_from_ast_kind(kind: parser::Kind) -> Type {
    match kind {
        parser::Kind::Nil   => Type::Nil,
        parser::Kind::Bool  => Type::Bool,
        parser::Kind::Int   => Type::Int,
        parser::Kind::Float => Type::Float,

        _ => panic!(format!("Unsupported kind {:?}", kind))
    }
}

impl IR {
    pub fn build(ast: &AST, scope: Shared<Scope>) -> Result<IR, BuildError> {
        let instruction = match *ast {
            AST::NilLiteral             => Instruction::Literal(ConstValue::Nil),
            AST::BoolLiteral  { value } => Instruction::Literal(ConstValue::Bool(value)),
            AST::IntLiteral   { value } => Instruction::Literal(ConstValue::Int(value)),
            AST::FloatLiteral { value } => Instruction::Literal(ConstValue::Float(value)),

            AST::Name { ref name } => {
                let variable = {
                    let scope = scope.borrow();

                    match scope.find_variable(name) {
                        Some(var) => var,
                        None => return Err(BuildError {
                            message: format!("Cannot find variable '{}'", name)
                        })
                    }
                };

                Instruction::VariableRef(VariableRef { variable })
            },

            // TODO: Use `may_be_var_call`
            AST::MethodCall(ref call) => {
                let mut args = vec![];

                args.push(IR::build(&call.target, Rc::clone(&scope))?);

                for arg in &call.args {
                    args.push(IR::build(arg, Rc::clone(&scope))?);
                }

                let function = {
                    let scope = scope.borrow();

                    match scope.find_function(&call.name) {
                        Some(f) => f,
                        None => return Err(BuildError {
                            message: format!("Cannot find function '{}'", call.name)
                        })
                    }
                };

                Instruction::Call(Call { function, args })
            },

            // TODO: Support catches
            AST::Block(ref block) => {
                let block = IR::build_block(block, Rc::clone(&scope))?;

                Instruction::Block(block)
            },

            _ => panic!(format!("Unsupported AST in IR: {:?}", ast))
        };

        Ok(IR {
            // ast,
            instruction,
            value_type: None
        })
    }

    fn build_block(block: &parser::BlockAST, scope: Shared<Scope>)
            -> Result<Block, BuildError> {
        let mut instructions = vec![];

        for expr in &block.exprs {
            instructions.push(IR::build(expr, Rc::clone(&scope))?);
        }

        if block.catches.len() > 0 {
            panic!("Catches are not supported in IR");
        }

        Ok(Block { code: instructions })
    }
}

impl Scope {
    pub fn find_function(&self, name: &str) -> Option<Shared<Function>> {
        self.runtime.borrow().functions.get(name).map(Rc::clone)
    }

    pub fn find_variable(&self, name: &str) -> Option<Shared<Variable>> {
        self.vars.get(name).map(Rc::clone)
    }
}
