use std::cell::RefCell;
use std::rc::Rc;

use types::*;
use core::*;

pub struct Core {
    macros: RefCell<Vec<Macro>>
}

impl NativeValue for Core {
    fn call(
        &self,
        method_name: &str,
        args: &[Value],
        location: Option<Location>
    ) -> Result<Value, Error> {
        match method_name {
            "fourty_two" => Ok(Value {
                object: Object::from(42),
                meta: Meta { location }
            }),

            "define_macro" => {
                expect_arg_count(2, args, &location)?;

                let name = expect_string(&args[0])?.into();
                let handler = expect_lambda(&args[1])?.clone();

                self.macros.borrow_mut().push(Macro { name, handler });

                Ok(Value {
                    object: Object::Nothing,
                    meta: Meta { location }
                })
            },

            _ => Err(Error::ExecError {
                message: String::from(format!("Unknown method '{}' on Core", method_name)),
                location
            })
        }
    }

    fn to_object(self) -> Object {
        Object::NativeValue(Rc::new(self))
    }
}

impl Core {
    pub fn new() -> Self {
        Core {
            macros: RefCell::new(Vec::new())
        }
    }

    pub fn get_macro(&self, name: &str) -> Option<Macro> {
        let macros = self.macros.borrow();

        for m in macros.iter() {
            if m.name == name {
                return Some(m.clone());
            }
        }

        None
    }
}
