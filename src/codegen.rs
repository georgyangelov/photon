use llvm::*;
use llvm::core::*;
use llvm::target::*;
use llvm::analysis::*;
use llvm::execution_engine::*;
use llvm::prelude::*;

use std::ptr;
use std::ffi::{CString, CStr};

use parser::*;

macro_rules! c_str {
    ($str: expr) => {
        concat!($str, "\0").as_ptr() as *const i8
    }
}

pub enum Type {
    Int,
    Float
}

pub struct RootEnv {
    context: LLVMContextRef,
    module: LLVMModuleRef,
}

impl Drop for RootEnv {
    fn drop(&mut self) {
        unsafe {
            LLVMDisposeModule(self.module);
            LLVMContextDispose(self.context);
        }
    }
}

impl RootEnv {
    pub fn new() -> RootEnv {
        unsafe {
            RootEnv {
                context: LLVMContextCreate(),
                module: LLVMModuleCreateWithName(c_str!("photon"))
            }
        }
    }

    pub fn generate(&self, ast: AST) {
        match ast {
            AST::MethodDef(ref method) => self.gen_method(method),
            _ => panic!("Unsupported AST type for code generation")
        }
    }

    fn gen_method(&self, method: &MethodDefAST) {

    }
}
