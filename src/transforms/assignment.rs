use types::*;

pub fn transform_all_assignments(values: Vec<Value>) -> Result<Vec<Value>, Error> {
    let mut exprs = values;
    let mut result = Vec::new();

    exprs.reverse();

    transform_exprs(&mut exprs, &mut result)?;

    Ok(result)
}

pub fn transform_assignments(value: Value) -> Result<Value, Error> {
    let mut exprs = vec![value];
    let mut result = Vec::new();

    transform_exprs(&mut exprs, &mut result)?;

    if result.len() != 1 {
        panic!("This shouldn't happen. transform_exprs([value]) != [new_value]");
    }

    Ok(result.pop().expect("This was already checked"))
}

fn transform_block(block: Block) -> Result<Block, Error> {
    let mut exprs = Vec::new();
    let mut block_exprs = block.exprs;
    block_exprs.reverse();

    transform_exprs(&mut block_exprs, &mut exprs)?;

    Ok(Block { exprs })
}

fn transform_exprs(exprs: &mut Vec<Value>, result: &mut Vec<Value>) -> Result<(), Error> {
    if exprs.is_empty() {
        return Ok(())
    }

    let expr = exprs.pop().expect("This queue wasn't empty when I looked, I promise!");

    let object = expr.object;
    let location = expr.meta.location;

    let new_object = match object {
        Object::Unknown |
        Object::Nothing |
        Object::Bool(_) |
        Object::Int(_) |
        Object::Float(_) |
        Object::Str(_) |
        Object::Struct(_) |
        Object::Op(Op::Call(_)) |
        Object::Op(Op::NameRef(_)) |
        Object::NativeValue(_) |
        Object::NativeLambda(_) => object,

        Object::Lambda(lambda) => {
            let body = transform_block(lambda.body)?;

            Object::Lambda(Lambda {
                params: lambda.params,
                body,
                scope: lambda.scope
            })
        },

        Object::Op(Op::Block(block)) => {
            Object::Op(Op::Block(transform_block(block)?))
        },

        Object::Op(Op::Assign(assign)) => {
            if exprs.is_empty() {
                assign.value.object
            } else {
                let mut body = Vec::new();
                transform_exprs(exprs, &mut body)?;

                let lambda = Lambda {
                    params: vec![Param { name: assign.name }],
                    body: Block { exprs: body },
                    scope: None
                };

                let lambda_call = Call {
                    target: Box::new(Value {
                        object: Object::Lambda(lambda),
                        meta: Meta { location: location.clone() }
                    }),
                    name: "$call".into(),
                    args: vec![*assign.value],
                    may_be_var_call: false,
                    module_resolve: false
                };

                Object::Op(Op::Call(lambda_call))
            }
        }
    };

    result.push(Value {
        object: new_object,
        meta: Meta { location: location.clone() }
    });

    transform_exprs(exprs, result)?;

    Ok(())
}
