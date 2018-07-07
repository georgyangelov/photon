#![macro_use]

use llvm::prelude::*;
use std::ffi::{CString, CStr};

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

pub unsafe fn llvm_str(str: &str) -> CString {
    CString::new(str).unwrap()
}

pub unsafe fn from_llvm_str(str: *const i8) -> Option<String> {
    CStr::from_ptr(str).to_str().ok().map( |str| String::from(str) )
}
