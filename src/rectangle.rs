// fn main() {
//     let str: String = String::from("Hello world!");
//
//     println!("{}", first_word(&str));
// }
//
// fn first_word(s: &str) -> &str {
//     let bytes = s.as_bytes();
//
//     for (i, &byte) in bytes.iter().enumerate() {
//         if byte == b' ' {
//             return &s[..i];
//         }
//     }
//
//     &s[..]
// }

#[derive(Debug)]
struct Rectangle {
    width: u32,
    height: u32
}

impl Rectangle {
    fn area(&self) -> u32 {
        self.width * self.height
    }

    fn can_hold(&self, other: &Rectangle) -> bool {
        self.width >= other.width && self.height >= other.height
    }
}

fn main() {
    let r1 = Rectangle { width: 30, height: 40 };
    let r2 = Rectangle { width: 20, height: 40 };

    println!("R1: {:?}", r1);
    println!("R2: {:?}", r2);
    println!();
    println!("R1 area: {}", r1.area());
    println!("R2 area: {}", r2.area());
    println!();
    println!("R1 >= R2: {}", r1.can_hold(&r2));
}
