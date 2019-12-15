use types::*;

mod parser_value;
pub use self::parser_value::*;

mod core;
pub use self::core::*;

pub fn expect_arg_count(count: usize, args: &[Value], location: &Option<Location>) -> Result<(), Error> {
    if args.len() != count {
        return Err(Error::ExecError {
            message: format!("Expected {} arguments, got {}", count, args.len()),
            location: location.clone()
        });
    }

    Ok(())
}

pub fn no_args(args: &[Value], location: &Option<Location>) -> Result<(), Error> {
    expect_arg_count(0, args, location)?;

    Ok(())
}

pub fn one_arg<'a>(args: &'a [Value], location: &Option<Location>) -> Result<&'a Value, Error> {
    expect_arg_count(1, args, location)?;

    Ok(&args[0])
}

pub fn expect_int(value: &Value) -> Result<i64, Error> {
    match &value.object {
        &Object::Int(result) => Ok(result),
        _ => Err(Error::ExecError {
            message: format!("Expected Int, got {:?}", value),
            location: value.meta.location.clone()
        })
    }
}

pub fn expect_string(value: &Value) -> Result<&str, Error> {
    match &value.object {
        &Object::Str(ref result) => Ok(result),
        _ => Err(Error::ExecError {
            message: format!("Expected String, got {:?}", value),
            location: value.meta.location.clone()
        })
    }
}

pub fn expect_lambda(value: &Value) -> Result<&Lambda, Error> {
    match &value.object {
        &Object::Lambda(ref result) => Ok(result),
        _ => Err(Error::ExecError {
            message: format!("Expected Lambda, got {:?}", value),
            location: value.meta.location.clone()
        })
    }
}
