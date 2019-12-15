use std::rc::Rc;
use parser::Parser;

use types::*;
use core::*;

pub struct ParserValue<'a> {
    pub parser: *mut Parser<'a>
}

impl NativeValue for ParserValue<'_> {
    fn call(
        &self,
        method_name: &str,
        args: &[Value],
        location: Option<Location>
    ) -> Result<Value, Error> {
        match method_name {
            "read_expr" => self.parser.parse_next(),

            _ => Err(Error::ExecError {
                message: String::from(format!("Unknown method '{}' on Parser", method_name)),
                location
            })
        }
    }

    fn to_object(self) -> Object {
        // Object::NativeValue(Rc::new(self))
        Object::Nothing
    }
}
