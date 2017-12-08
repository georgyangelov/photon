#![allow(unused_imports)]

use llvm::*;
use llvm::core::*;
use llvm::target::*;
use llvm::analysis::*;
use llvm::prelude::*;

use std::ptr;
use std::ffi::{CString, CStr};
use std::mem;

use parser::*;
use super::llvm_utils;

pub struct Compiler {
    pub(super) context: LLVMContextRef,
    pub(super) module: LLVMModuleRef
}

pub struct CompiledMethod<'a> {
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

    pub fn compile_method(&self, method: &MethodDefAST) -> CompiledMethod {
        let mut param_types = method.params.iter()
            .map( |param| self.llvm_type(param.kind) )
            .collect::<Vec<LLVMTypeRef>>();

        unsafe {
            let function_type = LLVMFunctionType(
                self.llvm_type(method.return_kind),
                param_types.as_mut_ptr(),
                param_types.len() as u32,
                llvm_utils::llvm_bool(false)
            );

            let function = LLVMAddFunction(
                self.module,
                llvm_utils::llvm_str(&method.name),
                function_type
            );

            let entry = LLVMAppendBasicBlock(function, c_str!("entry"));
            let builder = LLVMCreateBuilderInContext(self.context);

            LLVMPositionBuilderAtEnd(builder, entry);

            let return_value = self.build_block(builder, &method.body);

            LLVMBuildRet(builder, return_value);
            LLVMDisposeBuilder(builder);


            CompiledMethod {
                compiler: self,
                name: method.name.clone(),
                num_args: method.params.len()
            }
        }
    }

    unsafe fn build_block(&self, builder: LLVMBuilderRef, block: &BlockAST) -> LLVMValueRef {
        let mut last_expr = None;

        for ref expr in &block.exprs {
            last_expr = Some(self.build_expr(builder, &expr));
        }

        last_expr.expect("Empty block given to build_block. Unsupported for now")
    }

    unsafe fn build_expr(&self, builder: LLVMBuilderRef, ast: &AST) -> LLVMValueRef {
        match ast {
            &AST::BoolLiteral { value } => LLVMConstInt(
                self.llvm_type(Kind::Bool),
                value as u64,
                llvm_utils::llvm_bool(false)
            ),

            &AST::IntLiteral { value } => LLVMConstInt(
                self.llvm_type(Kind::Int),
                mem::transmute(value),
                llvm_utils::llvm_bool(true)
            ),

            &AST::FloatLiteral { value } => LLVMConstReal(
                self.llvm_type(Kind::Float),
                value
            ),

            &AST::Block(ref block) => self.build_block(builder, block),

            &AST::MethodCall(ref method_call) => self.build_method_call(builder, method_call),

            _ => panic!(format!("Unsupported AST type {:?} for build_expr", ast))
        }
    }

    unsafe fn build_method_call(&self, builder: LLVMBuilderRef, call: &MethodCallAST) -> LLVMValueRef {
        if call.args.len() != 1 {
            panic!(format!("Only binary operators are supported. Was given {:?}", call));
        }

        let arg_one = self.build_expr(builder, &call.target);
        let arg_two = self.build_expr(builder, &call.args[0]);

        match call.name.as_ref() {
            // TODO: Handle overflow
            "+" => LLVMBuildAdd(builder, arg_one, arg_two, c_str!("add_result")),
            "-" => LLVMBuildSub(builder, arg_one, arg_two, c_str!("sub_result")),
            "*" => LLVMBuildMul(builder, arg_one, arg_two, c_str!("mul_result")),

            // TODO: Check for division by zero
            "/" => LLVMBuildSDiv(builder, arg_one, arg_two, c_str!("div_result")),

            _ => panic!(format!("Unsupported operation {:?}", call))
        }
    }

    fn llvm_type(&self, kind: Kind) -> LLVMTypeRef {
        unsafe {
            match kind {
                Kind::Int   => LLVMInt64TypeInContext(self.context),
                Kind::Float => LLVMDoubleTypeInContext(self.context),
                Kind::Bool  => LLVMInt1TypeInContext(self.context),

                _ => panic!(format!("Unsupported type for codegen {:?}", kind))
            }
        }
    }
}

