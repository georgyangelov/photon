#![feature(io)]
#![feature(box_patterns)]

// #[macro_use]
extern crate itertools;
extern crate llvm_sys as llvm;

pub mod parser;
pub mod testing;
pub mod codegen;
