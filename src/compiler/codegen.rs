#![allow(unused_imports)]

use llvm::*;
use llvm::core::*;
use llvm::target::*;
use llvm::analysis::*;
use llvm::prelude::*;
use llvm::error_handling::*;

use std::ptr;
use std::ffi::{CString, CStr};
use std::mem;

use parser::*;
use super::llvm_utils;
use ir;

pub struct Compiler {
    pub(super) context: LLVMContextRef,
    pub(super) module: LLVMModuleRef
}

pub struct CompiledFunction<'a> {
    compiler: &'a Compiler,

    pub name: String,
    pub num_args: usize
}

impl Drop for Compiler {
    fn drop(&mut self) {
        unsafe {
            LLVMDisposeModule(self.module);
            LLVMContextDispose(self.context);
        }
    }
}

impl Compiler {
    pub fn new() -> Self {
        unsafe {
            let context = LLVMContextCreate();
            let module = LLVMModuleCreateWithNameInContext(c_str!("photon"), context);

            println!("LLVM Threading: {}", LLVMIsMultithreaded());

            // LLVMEnablePrettyStackTrace();
            // LLVMResetFatalErrorHandler();
            // LLVMInstallFatalErrorHandler(handle_llvm_fatal_error);

            Self {
                context: context,
                module: module
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

    pub fn print_ir(&self) {
        unsafe {
            LLVMDumpModule(self.module);
        }
    }

    pub fn compile(&self, f: &ir::Function) -> CompiledFunction {
        let mut param_types = f.params.iter()
            .map( |param| self.llvm_type(param.borrow().value_type) )
            .collect::<Vec<LLVMTypeRef>>();

        unsafe {
            let function_type = LLVMFunctionType(
                self.llvm_type(f.return_type),
                param_types.as_mut_ptr(),
                param_types.len() as u32,
                llvm_utils::llvm_bool(false)
            );

            let function = LLVMAddFunction(
                self.module,
                llvm_utils::llvm_str(&f.name),
                function_type
            );

            let entry = LLVMAppendBasicBlock(function, c_str!("entry"));
            let builder = LLVMCreateBuilderInContext(self.context);

            LLVMPositionBuilderAtEnd(builder, entry);

            if let ir::FunctionImpl::Photon(ref f_impl) = f.implementation {
                let return_value = self.build_block(builder, &f_impl.code);

                LLVMBuildRet(builder, return_value);
            } else {
                panic!(format!("Cannot compile {:?} function implementation", f.implementation));
            }

            LLVMDisposeBuilder(builder);

            CompiledFunction {
                compiler: self,
                name: f.name.clone(),
                num_args: f.params.len()
            }
        }
    }

    unsafe fn build_block(&self, builder: LLVMBuilderRef, block: &ir::Block) -> LLVMValueRef {
        let mut last_expr = None;

        for ref ir in &block.code {
            last_expr = Some(self.build_ir(builder, &ir));
        }

        last_expr.expect("Empty block given to build_block. Unsupported for now")
    }

    unsafe fn build_ir(&self, builder: LLVMBuilderRef, ir: &ir::IR) -> LLVMValueRef {
        match &ir.instruction {
            &ir::Instruction::Literal(ref value) => self.build_const_value(value),
            &ir::Instruction::Block(ref block) => self.build_block(builder, block),
            &ir::Instruction::Call(ref call) => self.build_call(builder, call),

            _ => panic!(format!("Unsupported IR type {:?} for build_ir", ir))
        }
    }

    unsafe fn build_const_value(&self, const_value: &ir::ConstValue) -> LLVMValueRef {
        match const_value {
            &ir::ConstValue::Bool(value) => LLVMConstInt(
                self.llvm_type(ir::Type::Bool),
                value as u64,
                llvm_utils::llvm_bool(false)
            ),

            &ir::ConstValue::Int(value) => LLVMConstInt(
                self.llvm_type(ir::Type::Int),
                mem::transmute(value),
                llvm_utils::llvm_bool(true)
            ),

            &ir::ConstValue::Float(value) => LLVMConstReal(
                self.llvm_type(ir::Type::Float),
                value
            ),

            _ => panic!(format!("build_const_value: Unsupported const value type {:?}", const_value))
        }
    }

    unsafe fn build_call(&self, builder: LLVMBuilderRef, call: &ir::Call) -> LLVMValueRef {
        // TODO: Check based on the function
        if call.args.len() != 2 {
            panic!(format!("Only binary operators are supported. Was given {:?}", call));
        }

        match call.function.borrow().implementation {
            ir::FunctionImpl::Intrinsic => self.build_intrinsic_call(builder, call),
            ir::FunctionImpl::Photon(_) => panic!("Calling Photon functions is not currently supported")
        }
    }

    unsafe fn build_intrinsic_call(&self, builder: LLVMBuilderRef, call: &ir::Call) -> LLVMValueRef {
        // assert!(call.function.borrow().implementation == ir::FunctionImpl::Intrinsic);

        let arg_one = self.build_ir(builder, &call.args[0]);
        let arg_two = self.build_ir(builder, &call.args[1]);

        match call.function.borrow().name.as_ref() {
            // TODO: Handle overflow
            "+" => LLVMBuildAdd(builder, arg_one, arg_two, c_str!("add_result")),
            "-" => LLVMBuildSub(builder, arg_one, arg_two, c_str!("sub_result")),
            "*" => LLVMBuildMul(builder, arg_one, arg_two, c_str!("mul_result")),

            // TODO: Check for division by zero
            "/" => LLVMBuildSDiv(builder, arg_one, arg_two, c_str!("div_result")),

            _ => panic!(format!("Unsupported intrinsic {:?}", call))
        }
    }

    fn llvm_type(&self, t: ir::Type) -> LLVMTypeRef {
        unsafe {
            match t {
                ir::Type::Int   => LLVMInt64TypeInContext(self.context),
                ir::Type::Float => LLVMDoubleTypeInContext(self.context),
                ir::Type::Bool  => LLVMInt1TypeInContext(self.context),

                _ => panic!(format!("Unsupported type for codegen {:?}", t))
            }
        }
    }
}

extern fn handle_llvm_fatal_error(reason: *const i8) {
    unsafe {
        let message = CStr::from_ptr(reason).to_str().unwrap();

        panic!(format!("LLVM error: {}", message));
    }
}
