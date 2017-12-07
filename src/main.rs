extern crate photon;

use photon::parser::*;
use photon::codegen::*;

fn main() {
    // let input = &mut std::io::stdin();
    let input = &mut std::fs::File::open("test.ph").expect("Cannot open input file");

    let lexer = Lexer::new("test.ph", input);
    let mut parser = Parser::new(lexer);

    let token = parser.parse_next().expect("Could not parse first expression");

    println!("{:?}", token);
    println!();

    let mut compiler = Compiler::new();

    if let AST::MethodDef(ref method_ast) = token {
        compiler.compile_method(method_ast);
    } else {
        panic!(format!("Expected token was MethodDef, got {:?}", token));
    }

    compiler.print_ir();
}
