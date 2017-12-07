extern crate photon;

use photon::parser::*;
use photon::codegen::*;

fn main() {
    // let input = &mut std::io::stdin();
    let mut input = std::fs::File::open("test.ph").expect("Cannot open input file");

    let lexer = Lexer::new("test.ph", &mut input);
    let mut parser = Parser::new(lexer);

    let token = parser.parse_next().expect("Could not parse first expression");

    println!("{:?}", token);
    println!();

    let compiler = Compiler::new();

    if let AST::MethodDef(ref method_ast) = token {
        let method = compiler.compile_method(method_ast);

        if let Err(reason) = compiler.verify_module() {
            panic!(format!("Module is not valid: {}", reason));
        }

        compiler.print_ir();

        unsafe {
            let return_value: i64 = method.call_2(5, 2);

            println!("Return value: {}", return_value);
        }
    } else {
        panic!(format!("Expected token was MethodDef, got {:?}", token));
    }
}
