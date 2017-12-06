#![allow(unused_imports)]

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

pub struct Compiler {
    context: LLVMContextRef,
    module: LLVMModuleRef,
    execution_engine: LLVMExecutionEngineRef
}

impl Drop for Compiler {
    fn drop(&mut self) {
        unsafe {
            LLVMDisposeModule(self.module);
            LLVMContextDispose(self.context);
            LLVMDisposeExecutionEngine(self.execution_engine);
        }
    }
}

impl Compiler {
    pub fn new() -> Self {
        unsafe {
            let context = LLVMContextCreate();
            let module = LLVMModuleCreateWithNameInContext(c_str!("photon"), context);

            llvm_init_jit();

            let engine = create_execution_engine(module);

            Self {
                context: context,
                module: module,
                execution_engine: engine.expect("Could not create execution engine")
            }
        }
    }

    pub fn verify_module(&self) -> Result<(), String> {
        unsafe {
            let mut result: Result<(), String> = Ok(());
            let mut error = ptr::null_mut() as *mut i8;

            LLVMVerifyModule(
                self.module,
                LLVMVerifierFailureAction::LLVMReturnStatusAction,
                &mut error as *mut *mut i8
            );

            if !error.is_null() {
                let message = CStr::from_ptr(error).to_str().unwrap();

                if message.len() > 0 {
                    result = Err(message.to_owned());
                }
            }

            LLVMDisposeMessage(error);

            result
        }
    }

    // pub fn compile(&self, ast: AST) {
    //     match ast {
    //         AST::MethodDef(ref method) => self.compile_method(method),
    //         _ => panic!("Unsupported AST type for code generation {:?}", ast)
    //     }
    // }

    pub fn compile_method(&mut self, method: &MethodDefAST) -> CompiledMethod {
        let mut param_types = method.params.iter()
            .map( |param| self.llvm_type(&param.kind) )
            .collect::<Vec<LLVMTypeRef>>();

        unsafe {
            let function_type = LLVMFunctionType(
                self.llvm_type(&method.return_kind),
                param_types.as_mut_ptr(),
                param_types.len() as u32,
                llvm_bool(false)
            );

            let function = LLVMAddFunction(
                self.module,
                llvm_str(&method.name),
                function_type
            );

            let entry = LLVMAppendBasicBlock(function, c_str!("entry"));
            let builder = LLVMCreateBuilderInContext(self.context);

            LLVMPositionBuilderAtEnd(builder, entry);

            // let return_value = self.build_block(builder, &method.body);

            // LLVMBuildRet(builder, return_value);
            LLVMDisposeBuilder(builder);

            CompiledMethod { compiler: self, llvm_ref: function }
        }
    }

    // unsafe fn build_block(&self, builder: LLVMBuilderRef, block: &BlockAST) -> LLVMValueRef {
    //     for ref expr in block.exprs {
    //         self.build_expr(builder, &expr);
    //     }
    // }

    // unsafe fn build_expr(&self, builder: LLVMBuilderRef, ast: &AST) -> LLVMValueRef {
    //     match ast {
    //         // TODO
    //     }
    // }

    fn llvm_type(&self, kind: &Kind) -> LLVMTypeRef {
        unsafe {
            match kind {
                &Kind::Int   => LLVMInt64TypeInContext(self.context),
                &Kind::Float => LLVMDoubleTypeInContext(self.context),

                _ => panic!(format!("Unsupported type for codegen {:?}", kind))
            }
        }
    }
}

pub struct CompiledMethod<'a> {
    compiler: &'a Compiler,
    llvm_ref: LLVMValueRef
}

// impl<'a> CompiledMethod<'a> {
//     pub fn run<T>(&self, args: &[i64]) -> i64 {
//         let fn: T = std::mem::transmute()
//     }
// }

fn llvm_bool(value: bool) -> LLVMBool {
    if value {
        1
    } else {
        0
    }
}

unsafe fn llvm_str(str: &str) -> *const i8 {
    str.as_ptr() as *const i8
}

unsafe fn llvm_init_jit() {
    LLVMLinkInMCJIT();
    LLVM_InitializeNativeTarget();
    LLVM_InitializeNativeAsmPrinter();
    LLVM_InitializeNativeAsmParser();
}

unsafe fn create_execution_engine(module: LLVMModuleRef) -> Result<LLVMExecutionEngineRef, String> {
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
