use std::rc::Rc;
use std::cell::RefCell;
use std::collections::HashMap;

use types::*;
use lexer::Lexer;
use parser::Parser;

pub type Shared<T> = Rc<RefCell<T>>;

pub fn share<T>(value: T) -> Shared<T> {
    Rc::new(RefCell::new(value))
}

struct Scope {
    parent: Option<Shared<Scope>>,
    vars: HashMap<String, Value>
}

impl Scope {
    fn new_root() -> Self {
        Scope {
            parent: None,
            vars: HashMap::new()
        }
    }

    fn new(parent: Shared<Scope>) -> Self {
        Scope {
            parent: Some(parent),
            vars: HashMap::new()
        }
    }

    fn get(&self, name: &str) -> Option<Value> {
        self.vars.get(name)
            .map(Clone::clone)
            .or_else( || self.parent.as_ref().and_then( |parent| parent.borrow().get(name) ) )
    }
}

struct Compiler {
    root_scope: Shared<Scope>
}

impl Compiler {
    pub fn new() -> Self {
        Self {
            root_scope: share(Scope::new_root())
        }
    }

    pub fn eval(&mut self, file_name: &str, source: &str) -> Result<Value, Error> {
        let mut input = source.as_bytes();
        let lexer = Lexer::new(file_name, &mut input);
        let mut parser = Parser::new(lexer);
        let mut value = Value {
            object: Object::Nothing,
            meta: Meta { location: None }
        };

        let root_scope = self.root_scope.clone();

        while parser.has_more_tokens()? {
            value = parser.parse_next()?;
            value = self.eval_value(&root_scope.borrow(), value)?;
        }

        Ok(value)
    }

    fn eval_value(&mut self, scope: &Scope, value: Value) -> Result<Value, Error> {
        let object = value.object;

        Ok(match object {
            Object::Unknown |
            Object::Nothing |
            Object::Struct(_) |
            Object::NativeValue(_) |
            // TODO: Try to partially evaluate body of lambda
            Object::Lambda(_) |
            Object::NativeLambda(_) => Value {
                object,
                meta: value.meta
            },

            Object::Op(Op::Block(block)) => {
                let mut unevaluated = Vec::new();
                let mut last_expr = Value {
                    object: Object::Nothing,
                    meta: Meta { location: value.meta.location.clone() }
                };

                for value in block.exprs {
                    let result = self.eval_value(scope, value)?;

                    if !is_evaluated(&result) {
                        unevaluated.push(result);
                    } else {
                        last_expr = result;
                    }
                }

                if unevaluated.len() > 0 {
                    Value {
                        object: Object::Op(Op::Block(Block { exprs: unevaluated })),
                        meta: Meta { location: value.meta.location.clone() }
                    }
                } else {
                    last_expr
                }
            },

            Object::Op(Op::NameRef(name_ref)) => {
                if let Some(value_in_scope) = scope.get(&name_ref.name) {
                    if is_unknown(&value_in_scope) {
                        Value {
                            object: Object::Op(Op::NameRef(name_ref)),
                            meta: value.meta
                        }
                    } else {
                        value_in_scope
                    }
                } else {
                    return Err(Error::ExecError {
                        message: format!("Undeclared identifier '{}'", name_ref.name),
                        location: value.meta.location
                    });
                }
            },

            Object::Op(Op::Call(call)) => {
                let mut args = Vec::new();
                let mut has_unevaluated = false;

                let target = self.eval_value(scope, *call.target)?;

                if !is_evaluated(&target) {
                    has_unevaluated = true;
                }

                for arg in call.args {
                    let result = self.eval_value(scope, arg)?;

                    if !is_evaluated(&result) {
                        has_unevaluated = true;
                    }

                    args.push(result);
                }

                let location = value.meta.location.clone();

                let call = Call {
                    args,
                    target: Box::new(target),
                    name: call.name,
                    module_resolve: call.module_resolve,
                    may_be_var_call: call.may_be_var_call
                };

                if has_unevaluated {
                    return Ok(Value {
                        object: Object::Op(Op::Call(call)),
                        meta: Meta { location }
                    });
                }

                self.call(call)?
            }
        })
    }

    fn call(&self, call: Call) -> Result<Value, Error> {
        let target_object = &call.target.object;

        match *target_object {
            // Object::Struct(Struct::Value)
            _ => panic!("Calling methods is not supported yet")
        }
    }
}

fn is_evaluated(value: &Value) -> bool {
    match value.object {
        Object::Op(_) => false,
        _ => true
    }
}

fn is_unknown(value: &Value) -> bool {
    match value.object {
        Object::Unknown => true,
        _ => false
    }
}
