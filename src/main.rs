extern crate photon;

use std::rc::Rc;

use photon::parser::*;
use photon::compiler::*;
use photon::ir;

fn main() {
    // let input = &mut std::io::stdin();
    let mut input = std::fs::File::open("test.ph").expect("Cannot open input file");

    let lexer = Lexer::new("test.ph", &mut input);
    let mut parser = Parser::new(lexer);

    let token = parser.parse_next().expect("Could not parse first expression");

    println!("{:?}", token);
    println!();

    let compiler = Compiler::new();
    let jit = jit::JIT::new(&compiler);
    let runtime = ir::Runtime::new();

    if let AST::MethodDef(ref method_ast) = token {
        let function = ir::Function::build(method_ast, Rc::clone(&runtime))
            .expect("Could not build function to IR");

        runtime.borrow_mut().add_function(&function);

        let method = compiler.compile(&function.borrow());

        if let Err(reason) = compiler.verify_module() {
            panic!(format!("Module is not valid: {}", reason));
        }

        compiler.print_ir();

        unsafe {
            let return_value = jit.call_2::<i64, i64, i64>(&method, 5, 2);

            println!("Return value: {}", return_value);
        }
    } else {
        panic!(format!("Expected token was MethodDef, got {:?}", token));
    }
}
