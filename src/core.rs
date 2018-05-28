use super::ir;

pub fn add_core(shared_runtime: ir::Shared<ir::Runtime>) {
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
    params: Vec<ir::Shared<ir::Variable>>,
    return_type: ir::Type
) {
    let function = ir::make_shared(ir::Function {
        name: String::from(name),
        params,
        return_type,
        implementation: ir::FunctionImpl::Intrinsic
    });

    runtime.add_function(&function);
}

fn param(name: &str, value_type: ir::Type) -> ir::Shared<ir::Variable> {
    ir::make_shared(ir::Variable {
        name: String::from(name),
        value_type
    })
}
