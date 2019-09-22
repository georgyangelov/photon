use std::fmt;
use std::rc::Rc;
use types::*;

pub struct BoolValue {
    pub value: bool
}

impl NativeValue for BoolValue {
    fn call(&self, name: String, _args: &[Value]) -> Result<Value, Error> {
        match name.as_str() {
            _ => Err(Error::ExecError {
                message: format!("Unknown method {} on BoolValue", name).into(),
                location: None
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl fmt::Debug for BoolValue {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{:?}", self.value)
    }
}

impl From<bool> for BoolValue {
    fn from(value: bool) -> Self {
        Self { value }
    }
}

impl From<bool> for Object {
    fn from(value: bool) -> Self {
        BoolValue::from(value).to_object()
    }
}
