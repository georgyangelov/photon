use ::core::*;
use ::interpreter::InterpreterError;

pub struct CoreLib {
    pub struct_module: Shared<Module>
}

impl CoreLib {
    pub fn new() -> CoreLib {
        CoreLib {
            struct_module: build_struct_module()
        }
    }
}

fn build_struct_module() -> Shared<Module> {
    let mut module = Module::new("Struct");

    def(&mut module, "include", vec!["self", "module"], |i, _scope, args| {
        let this = args[0].expect_struct()
            .ok_or_else(|| error(format!("Cannot call include on non-structs {:?}", this)))?;

        let module = args[1].expect_module()
            .ok_or_else(|| error("Struct#include needs a module as an argument".into()))?;

        let this_module = i.find_name_in_struct("__module__", this)
            .flat_map( |m| m.expect_module() )
            .ok_or_else(|| error("Struct#__module__ is not a module".into()))?;

        this_module.borrow_mut().include(module);

        Ok(this.clone())
    });

    make_shared(module)
}

fn def(module: &mut Module, name: &str, params: Vec<&str>, function: RustFunction) {
    let signature = FnSignature {
        name: String::from(name),
        params: params.iter().map( |name| FnParam { name: String::from(*name) } ).collect()
    };

    let implementation = FnImplementation::Rust(Box::new(function));

    module.add_function(name, make_shared(Function {
        signature,
        implementation
    }));
}

fn error(message: String) -> InterpreterError {
    InterpreterError { message }
}
