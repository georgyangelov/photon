use ::data_structures::{ir, Shared, make_shared};
use super::intrinsics;

#[derive(Debug)]
pub struct Interpreter {
    pub runtime: Shared<ir::Runtime>
}

pub enum Value {
    None,
    Bool(bool),
    Int(i64),
    Float(f64),
    Struct(StructValue)
}

impl From<bool> for Value {
    fn from(value: bool) -> Self {
        Value::Bool(value)
    }
}

impl From<i64> for Value {
    fn from(value: i64) -> Self {
        Value::Int(value)
    }
}

impl From<f64> for Value {
    fn from(value: f64) -> Self {
        Value::Float(value)
    }
}

pub struct Struct {
    value: *()
}

pub enum InterpreterError {
    // TODO: Better error handling
    ArgumentError
}

struct Scope {
    variables: HashMap<Shared<ir::Variable>, Value>
}

impl Scope {
    fn new() -> Self {
        Self { variables: HashMap::new() }
    }
}

impl Interpreter {
    pub fn new() -> Self {
        Self {
            runtime: make_shared(ir::Runtime::new())
        }
    }

    pub fn call(&mut self, f: Shared<ir::Function>, args: Vec<Value>) -> Result<Value, InterpreterError> {
        let implementation = f.borrow().implementation;

        match implementation {
            ir::FunctionImpl::Intrinsic => call_intrinsic(f, args),
            ir::FunctionImpl::Photon    => call_photon_fn(f, args)
        }
    }

    fn call_intrinsic(&mut self, f: &ir::Function, args: Vec<Value>) -> Result<Value, InterpreterError> {
        match f.name {
            "+" => intrinsics::operator_plus(args),
            "-" => intrinsics::operator_minus(args),
            "*" => intrinsics::operator_multiply(args),
            "/" => intrinsics::operator_divide(args)
        }
    }

    fn call_photon_fn(&mut self, f: &ir::Function, args: Vec<Value>) -> Result<Value, InterpreterError> {
        let mut scope = Scope::new();
        let params = f.signature.params;

        if args.len() != params.len() {
            return Err(InterpreterError::ArgumentError);
        }

        for (i, param) in params.iter().enumerate() {
            let value = args[i];

            scope.variables.put(param.clone(), value);
        }

        match f.implementation {
            ir::FunctionImpl::Intrinsic => panic!("call_photon_fn called with intrinsic function"),
            ir::FunctionImpl::Photon(ref photon_fn) => self.interpret(&photon_fn.code, &scope)
        }
    }

    fn interpret(&mut self, instruction: &ir::Instruction, scope: &mut Scope) -> Result<Value, InterpreterError> {
        match instruction {
            ir::Instruction::Literal(const_value) => {
                match const_value {
                    ir::ConstValue::Nil          => Value::None,
                    ir::ConstValue::Bool(value)  => Value::from(value),
                    ir::ConstValue::Int(value)   => Value::from(value)
                    ir::ConstValue::Float(value) => Value::from(value)
                }
            },

            ir::Instruction::Block(block) => {
                let instructions = &block.code;
                let mut last_value = Value::None;

                for instr in instructions {
                    last_value = self.interpret(instr)?;
                }

                last_value
            }

            ir::Instruction::VariableRef(var) => {

            },

            ir::Instruction::Call(call) => {
                // TODO: Implement
            }
        }
    }
}
