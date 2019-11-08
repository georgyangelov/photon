use std::rc::Rc;
use types::*;

#[derive(Debug)]
pub struct Core {
}

impl NativeValue for Core {
    fn call(&self, name: &str, _args: &[Value]) -> Result<Value, Error> {
        match name {
            // TODO: Pass location to these (but only compile-time)?
            "fourty_two" => Ok(Value {
                object: Object::from(42),
                meta: Meta { location: None }
            }),

            // TODO
            // "define_macro" => Ok(Value)

            _ => Err(Error::ExecError {
                message: String::from(format!("Unknown method '{}' on Core", name)),
                location: None
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl Core {
    pub fn new() -> Self {
        Core {}
    }
}
