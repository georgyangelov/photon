#![allow(unused_imports)]

use llvm::prelude::*;
use llvm::target::*;
use llvm::execution_engine::*;

use std::ptr;
use std::ffi::{CString, CStr};
use std::mem;

use super::*;
use super::llvm_utils;

pub struct JIT {
    // TODO: LLVM segfaults when disposing of this and the module at the same time
    execution_engine: LLVMExecutionEngineRef
}

impl JIT {
    pub fn new(compiler: &Compiler) -> Self {
        unsafe {
            // TODO: Can this be called more than once? Any downsides?
            llvm_init_jit();

            let execution_engine = create_llvm_execution_engine(compiler.module)
                .expect("Could not create execution engine");

            Self { execution_engine }
        }
    }

    pub unsafe fn call_0<R>(&self, method: &CompiledFunction) -> R {
        Self::validate_arg_count(method, 0);
        let function: extern fn() -> R = mem::transmute(self.get_address(method));

        function()
    }

    pub unsafe fn call_1<T1, R>(&self, method: &CompiledFunction, a1: T1) -> R {
        Self::validate_arg_count(method, 1);
        let function: extern fn(T1) -> R = mem::transmute(self.get_address(method));

        function(a1)
    }

    pub unsafe fn call_2<T1, T2, R>(&self, method: &CompiledFunction, a1: T1, a2: T2) -> R {
        Self::validate_arg_count(method, 2);
        let function: extern fn(T1, T2) -> R = mem::transmute(self.get_address(method));

        function(a1, a2)
    }

    pub unsafe fn call_3<T1, T2, T3, R>(&self, method: &CompiledFunction, a1: T1, a2: T2, a3: T3) -> R {
        Self::validate_arg_count(method, 3);
        let function: extern fn(T1, T2, T3) -> R = mem::transmute(self.get_address(method));

        function(a1, a2, a3)
    }

    fn validate_arg_count(method: &CompiledFunction, given: usize) {
        if method.num_args != given {
            panic!(format!(
                "Function expected {} arguments but you tried to call it with {}",
                method.num_args,
                given
            ));
        }
    }

    unsafe fn get_address(&self, method: &CompiledFunction) -> *const () {
        if method.name.len() < 3 {
            panic!(format!(
                "LLVM crashes on method names with less than 3 symbols. Given: {}",
                method.name
            ))
        }

        // This actually compiles the function
        let function_address = LLVMGetFunctionAddress(
            self.execution_engine,
            llvm_utils::llvm_str(&method.name)
        );

        if function_address == 0 {
            panic!(format!("Could not compile method {:?}", method.name));
        }

        function_address as *const ()
    }
}

unsafe fn llvm_init_jit() {
    LLVMLinkInMCJIT();
    LLVM_InitializeNativeTarget();
    LLVM_InitializeNativeAsmPrinter();
    LLVM_InitializeNativeAsmParser();
}

unsafe fn create_llvm_execution_engine(module: LLVMModuleRef) -> Result<LLVMExecutionEngineRef, String> {
    let mut engine = ptr::null_mut() as LLVMExecutionEngineRef;
    let mut error = ptr::null_mut() as *mut i8;

    let creation_result = LLVMCreateExecutionEngineForModule(
        &mut engine as *mut LLVMExecutionEngineRef,
        module,
        &mut error as *mut *mut i8
    );

    if creation_result == 0 {
        Ok(engine)
    } else {
        if let Ok(message) = CStr::from_ptr(error).to_str() {
            // DANGER
            Err(message.to_owned())
        } else {
            Err(String::from(
                "Could not create LLVM execution engine. No error message provided by LLVM"
            ))
        }
    }
}
