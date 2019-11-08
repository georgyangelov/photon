use std::collections::HashMap;

use core::*;
use types::*;
use lexer::Lexer;
use parser::Parser;
use transforms::transform_all_assignments;

pub struct Compiler {
    root_scope: Shared<Scope>
}

impl Compiler {
    pub fn new() -> Self {
        let mut root_scope = Scope::new_root();

        root_scope.vars.insert(String::from("Core"), Value {
            object: Core::new().to_object(),
            meta: Meta { location: None }
        });

        Self {
            root_scope: share(root_scope)
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

        let scope = self.root_scope.clone();
        let mut code = Vec::new();

        while parser.has_more_tokens()? {
            code.push(parser.parse_next()?);
        }

        let transformed_code = transform_all_assignments(code)?;

        for expr in transformed_code {
            value = eval_value(scope.clone(), expr)?;
        }

        Ok(value)
    }
}

// TODO: Does this need to be `Shared`?
fn eval_value(scope: Shared<Scope>, value: Value) -> Result<Value, Error> {
    let object = value.object;

    Ok(match object {
        Object::Unknown        |
        Object::Nothing        |
        Object::Bool(_)        |
        Object::Int(_)         |
        Object::Float(_)       |
        Object::Str(_)         |
        Object::Struct(_)      |
        Object::NativeValue(_) |
        Object::NativeLambda(_) => Value {
            object,
            meta: value.meta
        },

        Object::Lambda(Lambda { params, body, scope: _ }) => {
            let mut partial_eval_vars = HashMap::<String, Value>::new();

            for param in &params {
                partial_eval_vars.insert(param.name.clone(), Value {
                    // TODO: Get param location
                    meta: Meta { location: None },
                    object: Object::Unknown
                });
            }

            let partial_eval_scope = Scope::new(scope.clone(), partial_eval_vars);
            let result = eval_block(share(partial_eval_scope), body, &value.meta.location)?;

            let evaluated_body = if let Value { object: Object::Op(Op::Block(block)), .. } = result {
                block
            } else {
                Block { exprs: vec![result] }
            };

            Value {
                object: Object::Lambda(Lambda {
                    params,
                    body: evaluated_body,
                    scope: Some(scope.clone())
                }),
                meta: Meta { location: value.meta.location.clone() }
            }
        },

        Object::Op(Op::Assign(_)) => panic!("Cannot execute Assign, it's only for source code."),

        Object::Op(Op::Block(block)) => {
            eval_block(scope.clone(), block, &value.meta.location)?
        },

        Object::Op(Op::NameRef(name_ref)) => {
            if let Some(value_in_scope) = scope.borrow().get(&name_ref.name) {
                // TODO: Figure out this unknown here...
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
            let mut has_unevaluated_args = false;
            let var_call_target = if call.may_be_var_call {
                scope.borrow().get(&call.name)
            } else {
                None
            };

            let (target, method_name) = if let Some(target) = var_call_target {
                // TODO: Extract this logic from here and the nameref handler
                let known_target = if let Value { object: Object::Unknown, .. } = target {
                    Value {
                        object: Object::Op(Op::NameRef(NameRef { name: call.name.clone() })),
                        meta: call.target.meta
                    }
                } else {
                    target
                };

                (known_target, String::from("$call"))
            } else {
                let target = eval_value(scope.clone(), *call.target)?;

                (target, call.name)
            };

            let target_evaluated = is_evaluated(&target);

            for arg in call.args {
                let result = eval_value(scope.clone(), arg)?;

                if !is_evaluated(&result) {
                    has_unevaluated_args = true;
                }

                args.push(result);
            }

            let location = value.meta.location.clone();

            let call = Call {
                args,
                target: Box::new(target),
                name: method_name,
                module_resolve: call.module_resolve,
                may_be_var_call: false
            };

            if !target_evaluated || has_unevaluated_args {
                println!("Cannot evaluate {:?}", call);

                return Ok(Value {
                    object: Object::Op(Op::Call(call)),
                    meta: Meta { location }
                });
            }

            let call_2 = call.clone();
            println!("Evaluating {:?}", call_2);

            let result = call_function(call, &location)?;

            println!("Evaluated {:?} to {:?}", call_2, result);

            result
        }
    })
}

fn eval_block(scope: Shared<Scope>, block: Block, location: &Option<Location>) -> Result<Value, Error> {
    let mut unevaluated = Vec::new();
    let mut last_expr = None;

    for value in block.exprs {
        let result = eval_value(scope.clone(), value)?;

        if !is_evaluated(&result) {
            unevaluated.push(result);
        } else {
            last_expr = Some(result);
        }
    }

    let result = if unevaluated.len() > 0 {
        if let Some(last_expr) = last_expr {
            unevaluated.push(last_expr);
        }

        if unevaluated.len() == 1 {
            unevaluated.into_iter().nth(0).expect("See the line above")
        } else {
            Value {
                object: Object::Op(Op::Block(Block { exprs: unevaluated })),
                meta: Meta { location: location.clone() }
            }
        }
    } else {
        last_expr.unwrap_or(Value {
            object: Object::Nothing,
            meta: Meta { location: location.clone() }
        })
    };

    Ok(result)
}

fn call_function(call: Call, location: &Option<Location>) -> Result<Value, Error> {
    let target_object = &call.target.object;

    match *target_object {
        Object::Int(value) => method_call_on_int(value, &call.name, &call.args, location),
        Object::Lambda(ref lambda) => method_call_on_lambda(lambda, &call.name, &call.args, location),
        Object::NativeValue(ref native_value) => native_value.clone().call(&call.name, &call.args),

        _ => Err(Error::ExecError {
            message: format!("Cannot call methods on {:?}", target_object),
            location: location.clone()
        })
    }
}

fn method_call_on_int(
    value: i64,
    name: &str,
    args: &[Value],
    method_location: &Option<Location>
) -> Result<Value, Error> {
    match name {
        "+" => {
            let other = one_arg(args, method_location)?;
            let other = expect_int(&other)?;

            Ok(Value {
                object: Object::from(value + other),
                meta: Meta { location: method_location.clone() }
            })
        },

        _ => Err(Error::ExecError {
            message: format!("Unknown method {}", name),
            location: method_location.clone()
        })
    }
}

fn method_call_on_lambda(
    lambda: &Lambda,
    name: &str,
    args: &[Value],
    method_location: &Option<Location>
) -> Result<Value, Error> {
    match name {
        "$call" => call_lambda(lambda, args, method_location),

        _ => Err(Error::ExecError {
            message: format!("Unknown method {}", name),
            location: method_location.clone()
        })
    }
}

fn call_lambda(lambda: &Lambda, args: &[Value], call_location: &Option<Location>) -> Result<Value, Error> {
    expect_arg_count(lambda.params.len(), args, call_location)?;

    let mut params = HashMap::new();

    for (i, param) in lambda.params.iter().enumerate() {
        // TODO: Optimization idea - prevent this value clone here?
        //       Make Object clone by reference (Rc<Object>)?
        params.insert(param.name.clone(), args[i].clone());
    }

    let lambda_scope = lambda.scope.clone().expect("Lambda should have a scope");
    let scope = Scope::new(lambda_scope, params);

    eval_value(share(scope), Value {
        // TODO: Make this not clone
        object: Object::Op(Op::Block(lambda.body.clone())),
        meta: Meta { location: call_location.clone() }
    })
}

fn expect_arg_count(count: usize, args: &[Value], location: &Option<Location>) -> Result<(), Error> {
    if args.len() != count {
        return Err(Error::ExecError {
            message: format!("Expected {} arguments, got {}", count, args.len()),
            location: location.clone()
        });
    }

    Ok(())
}

fn no_args(args: &[Value], location: &Option<Location>) -> Result<(), Error> {
    expect_arg_count(0, args, location)?;

    Ok(())
}

fn one_arg<'a>(args: &'a [Value], location: &Option<Location>) -> Result<&'a Value, Error> {
    expect_arg_count(1, args, location)?;

    Ok(&args[0])
}

fn expect_int(value: &Value) -> Result<i64, Error> {
    match &value.object {
        &Object::Int(result) => Ok(result),
        _ => Err(Error::ExecError {
            message: format!("Expected Int, got {:?}", value),
            location: value.meta.location.clone()
        })
    }
}

fn is_evaluated(value: &Value) -> bool {
    match value.object {
        Object::Op(_) => false,
        Object::Unknown => false,
        _ => true
    }
}

fn is_unknown(value: &Value) -> bool {
    match value.object {
        Object::Unknown => true,
        _ => false
    }
}
