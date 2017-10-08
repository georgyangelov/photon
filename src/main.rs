extern crate photon;

use photon::parser::*;

fn main() {
    // let input = &mut std::io::stdin();
    let input = &mut std::fs::File::open("test.txt").expect("Cannot open input file");

    let mut lexer = Lexer::new("test.ph", input);

    println!("{:?}", lexer.next_token());
}
