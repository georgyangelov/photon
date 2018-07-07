#![feature(io)]
#![feature(box_patterns)]

// #[macro_use]
extern crate itertools;
extern crate llvm_sys as llvm;
extern crate libc;

pub mod analyzer;
pub mod compiler;
pub mod data_structures;
pub mod parser;
pub mod core;
pub mod testing;
pub mod ir_builder;
