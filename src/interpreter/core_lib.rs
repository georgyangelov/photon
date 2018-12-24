use ::core::*;
use ::interpreter::InterpreterError;
use im::vector::Vector;

pub struct CoreLib {
    pub root_scope: Shared<Scope>,

    pub module_module: Shared<Module>,
    pub string_module: Shared<Module>,
    pub struct_module: Shared<Module>,
    pub array_module:  Shared<Module>
}

impl CoreLib {
    pub fn new() -> CoreLib {
        let scope = Scope::new();

        let module_module;
        let string_module;
        let struct_module;
        let array_module;

        {
            let mut scope = scope.borrow_mut();

            module_module = define_module_module(&mut scope);
            string_module = define_string(&mut scope);
            struct_module = define_struct_module(&mut scope);
            array_module  = define_array(&mut scope);
        }

        CoreLib {
            root_scope: scope,
            module_module,
            string_module,
            struct_module,
            array_module
        }
    }
}

fn define_module_module(scope: &mut Scope) -> Shared<Module> {
    let module = make_shared(Module::new("Module"));

    module.borrow_mut().def("name", vec!["self"], |_i, _scope, args| {
        let this = args[0].expect_module()
            .ok_or_else(|| error("Must be a module".into()))?;

        let module = this.borrow();

        Ok(Value::String(module.name.clone()))
    });

    scope.assign(Variable {
        name: module.borrow().name.clone(),
        value: Value::Module(module.clone())
    });

    module
}

fn define_module(scope: &mut Scope, name: &str) -> Shared<Module> {
    let mut module = Module::new(name);
    let module_module = find_module(scope, "Module").expect("Module `Module` does not exist");

    module.supermodules.push(module_module);

    let shared_module = make_shared(module);

    scope.assign(Variable {
        name: name.to_string(),
        value: Value::Module(shared_module.clone())
    });

    shared_module
}

fn define_struct_module(scope: &mut Scope) -> Shared<Module> {
    let module = define_module(scope, "Struct");

    // TODO: Remove this and implement directly in Photon
    module.borrow_mut().def("include", vec!["self", "module"], |i, _scope, args| {
        let this = args[0].expect_struct()
            .ok_or_else(|| error("Cannot call include on non-structs".into()))?;

        let module = args[1].expect_module()
            .ok_or_else(|| error("Struct#include needs a module as an argument".into()))?;

        let this_module = i.find_name_in_struct("$module", this)
            .and_then( |value| value.expect_module() )
            .ok_or_else(|| error("Struct#$module is not a module".into()))?;

        this_module.borrow_mut().include(module);

        Ok(args[0].clone())
    });

    module
}

fn define_string(scope: &mut Scope) -> Shared<Module> {
    let module = define_module(scope, "String");
    let supermodule = define_module(scope, "StringStaticModule");
    extend(module.clone(), supermodule.clone());

    supermodule.borrow_mut().def("new", vec!["self"], |_i, _scope, _args| {
        Ok(Value::String(String::new()))
    });

    module.borrow_mut().def("size", vec!["self"], |_i, _scope, args| {
        let this = args[0].expect_string()
            .ok_or_else(|| error("Must be a string".into()))?;

        Ok(Value::Int(this.len() as i64))
    });

    module
}

fn define_array(scope: &mut Scope) -> Shared<Module> {
    let module = define_module(scope, "Array");
    let supermodule = define_module(scope, "ArrayStaticModule");
    extend(module.clone(), supermodule.clone());

    supermodule.borrow_mut().def("new", vec!["self"], |_i, _scope, _args| {
        Ok(Value::Array(Vector::new()))
    });

    module.borrow_mut().def("size", vec!["self"], |_i, _scope, args| {
        let this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        Ok(Value::Int(this.len() as i64))
    });

    module.borrow_mut().def("get", vec!["self", "i"], |_i, _scope, args| {
        let this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        let index = args[1].expect_int()
            .ok_or_else(|| error("Second argument must be an int".into()))?;

        // TODO: Return Some/None
        Ok(this.get(index as usize).unwrap().clone())
    });

    module.borrow_mut().def("set", vec!["self", "i", "value"], |_i, _scope, args| {
        let mut this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        let index = args[1].expect_int()
            .ok_or_else(|| error("Second argument must be an int".into()))?;

        let value = args[2].clone();

        this.set(index as usize, value);

        Ok(Value::Array(this))
    });

    module.borrow_mut().def("push", vec!["self", "value"], |_i, _scope, args| {
        let mut this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        let value = args[2].clone();

        this.push_back(value);

        Ok(Value::Array(this))
    });

    module.borrow_mut().def("pop", vec!["self"], |_i, _scope, args| {
        let mut this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        // TODO: Return Some/None
        Ok(this.pop_back().unwrap().clone())
    });

    module.borrow_mut().def("insert", vec!["self", "i", "value"], |_i, _scope, args| {
        let mut this = args[0].expect_array()
            .ok_or_else(|| error("Must be an array".into()))?;

        let index = args[1].expect_int()
            .ok_or_else(|| error("Second argument must be an int".into()))?;

        let value = args[2].clone();

        this.insert(index as usize, value);

        Ok(Value::Array(this))
    });

    module
}

fn extend(this: Shared<Module>, with: Shared<Module>) {
    this.borrow_mut().supermodules.insert(0, with);
}

fn find_module(scope: &Scope, name: &str) -> Option<Shared<Module>> {
    scope.get(name)
        .map( |var| var.value )
        .and_then( |value| value.expect_module() )
}

fn error(message: String) -> InterpreterError {
    InterpreterError { message }
}