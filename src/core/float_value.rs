use std::fmt;
use std::rc::Rc;
use types::*;

pub struct FloatValue {
    pub value: f64
}

impl NativeValue for FloatValue {
    fn call(&self, name: String, _args: &[Value]) -> Result<Value, Error> {
        match name.as_str() {
            _ => Err(Error::ExecError {
                message: format!("Unknown method {} on FloatValue", name).into(),
                location: None
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl fmt::Debug for FloatValue {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{:?}", self.value)
    }
}

impl From<f64> for FloatValue {
    fn from(value: f64) -> Self {
        Self { value }
    }
}

impl From<f64> for Object {
    fn from(value: f64) -> Self {
        FloatValue::from(value).to_object()
    }
}
