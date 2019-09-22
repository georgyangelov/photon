use std::fmt;
use std::rc::Rc;
use types::*;

pub struct IntValue {
    pub value: i64
}

impl NativeValue for IntValue {
    fn call(&self, name: String, _args: &[Value]) -> Result<Value, Error> {
        match name.as_str() {
            "fourty_two" => Ok(Value {
                object: Object::from(42),
                meta: Meta { location: None }
            }),

            _ => Err(Error::ExecError {
                message: format!("Unknown method {} on IntValue", name).into(),
                location: None
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl fmt::Debug for IntValue {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{:?}", self.value)
    }
}

impl From<i64> for IntValue {
    fn from(value: i64) -> Self {
        Self { value }
    }
}

impl From<i64> for Object {
    fn from(value: i64) -> Self {
        IntValue::from(value).to_object()
    }
}
