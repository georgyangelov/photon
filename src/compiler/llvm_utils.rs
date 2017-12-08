#![macro_use]

use llvm::prelude::*;

macro_rules! c_str {
    ($str: expr) => {
        concat!($str, "\0").as_ptr() as *const i8
    }
}

pub fn llvm_bool(value: bool) -> LLVMBool {
    if value {
        1
    } else {
        0
    }
}

pub unsafe fn llvm_str(str: &str) -> *const i8 {
    str.as_ptr() as *const i8
}
