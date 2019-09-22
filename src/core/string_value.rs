use std::fmt;
use std::rc::Rc;
use types::*;

pub struct StringValue {
    pub value: String
}

impl NativeValue for StringValue {
    fn call(&self, name: String, _args: &[Value]) -> Result<Value, Error> {
        match name.as_str() {
            "hello" => Ok(Value {
                object: Object::from("Hello world!"),
                meta: Meta { location: None }
            }),

            _ => Err(Error::ExecError {
                message: format!("Unknown method {} on StringValue", name).into(),
                location: None
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl fmt::Debug for StringValue {
    fn fmt(&self, f: &mut fmt::Formatter) -> Result<(), fmt::Error> {
        write!(f, "{:?}", self.value)
    }
}

impl From<&str> for StringValue {
    fn from(value: &str) -> Self {
        Self { value: String::from(value) }
    }
}

impl From<String> for StringValue {
    fn from(value: String) -> Self {
        Self { value }
    }
}

impl From<&str> for Object {
    fn from(value: &str) -> Self {
        StringValue::from(value).to_object()
    }
}

impl From<String> for Object {
    fn from(value: String) -> Self {
        StringValue::from(value).to_object()
    }
}
