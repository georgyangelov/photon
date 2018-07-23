use super::interpreter::{
    Value,
    InterpreterError
};

pub fn operator_plus(args: &[Value]) -> Result<Value, InterpreterError> {
    expect_arg_count(args, 2)?;

    match (args[0], args[1]) {
        (Value::Int(a), Value::Int(b))     => Ok(Value::from(a + b)),
        (Value::Float(a), Value::Float(b)) => Ok(Value::from(a + b)),

        _ => Err(InterpreterError::ArgumentError)
    }
}

pub fn operator_minus(args: &[Value]) -> Result<Value, InterpreterError> {
    expect_arg_count(args, 2)?;

    match (args[0], args[1]) {
        (Value::Int(a), Value::Int(b))     => Ok(Value::from(a - b)),
        (Value::Float(a), Value::Float(b)) => Ok(Value::from(a - b)),

        _ => Err(InterpreterError::ArgumentError)
    }
}

pub fn operator_multiply(args: &[Value]) -> Result<Value, InterpreterError> {
    expect_arg_count(args, 2)?;

    match (args[0], args[1]) {
        (Value::Int(a), Value::Int(b))     => Ok(Value::from(a * b)),
        (Value::Float(a), Value::Float(b)) => Ok(Value::from(a * b)),

        _ => Err(InterpreterError::ArgumentError)
    }
}

pub fn operator_divide(args: &[Value]) -> Result<Value, InterpreterError> {
    expect_arg_count(args, 2)?;

    match (args[0], args[1]) {
        (Value::Int(a), Value::Int(b))     => Ok(Value::from(a / b)),
        (Value::Float(a), Value::Float(b)) => Ok(Value::from(a / b)),

        _ => Err(InterpreterError::ArgumentError)
    }
}

fn expect_arg_count(args: &[Value], count: i32) -> Result<(), InterpreterError> {
    if args.size() != count {
        return Err(InterpreterError::ArgumentError);
    }
}
