use ::data_structures::{ir, make_shared, Shared};

pub fn add_core(shared_runtime: Shared<ir::Runtime>) {
    let mut runtime = shared_runtime.borrow_mut();

    create_intrinsic_fn(
        &mut runtime,
        "+",
        vec![param("a", ir::Type::Int), param("b", ir::Type::Int)],
        ir::Type::Int
    );

    create_intrinsic_fn(
        &mut runtime,
        "-",
        vec![param("a", ir::Type::Int), param("b", ir::Type::Int)],
        ir::Type::Int
    );

    create_intrinsic_fn(
        &mut runtime,
        "*",
        vec![param("a", ir::Type::Int), param("b", ir::Type::Int)],
        ir::Type::Int
    );

    create_intrinsic_fn(
        &mut runtime,
        "/",
        vec![param("a", ir::Type::Int), param("b", ir::Type::Int)],
        ir::Type::Int
    );
}

fn create_intrinsic_fn(
    runtime: &mut ir::Runtime,
    name: &str,
    params: Vec<Shared<ir::Variable>>,
    return_type: ir::Type
) {
    let function = make_shared(ir::Function {
        name: String::from(name),
        signature: ir::FunctionSignature { params, return_type },
        implementation: ir::FunctionImpl::Intrinsic
    });

    runtime.functions.insert(String::from(name), function);
}

fn param(name: &str, value_type: ir::Type) -> Shared<ir::Variable> {
    make_shared(ir::Variable {
        name: String::from(name),
        value_type
    })
}
