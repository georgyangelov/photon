#![allow(unused_imports)]

use llvm::prelude::*;
use llvm::target::*;
use llvm::target_machine::*;
use llvm::orc::*;
use llvm::core::*;

use std::ptr;
use std::ffi::{CString, CStr};
use std::mem;

use libc;

use super::*;
use super::llvm_utils;

struct ResolverContext {
    orc: LLVMOrcJITStackRef
}

pub struct JIT<'a> {
    compiler: &'a Compiler,

    orc: LLVMOrcJITStackRef,
    shared_module: LLVMSharedModuleRef,
    module_handle: LLVMOrcModuleHandle,
    resolver_context: *mut ResolverContext
}

impl<'a> Drop for JIT<'a> {
    fn drop(&mut self) {
        unsafe {
            Box::from_raw(self.resolver_context);

            LLVMOrcDisposeSharedModuleRef(self.shared_module);

            // TODO: This crashes, figure it out
            // match LLVMOrcDisposeInstance(self.orc) {
            //     LLVMOrcErrorCode::LLVMOrcErrSuccess => (),
            //     _ => panic!("Could not dispose of ORC instance")
            // };
        }
    }
}

impl<'a> JIT<'a> {
    pub fn new(compiler: &'a Compiler) -> Self {
        let module = compiler.module;

        unsafe {
            LLVM_InitializeNativeTarget();
            LLVM_InitializeNativeAsmPrinter();
            LLVM_InitializeNativeAsmParser();

            let target_ref = build_target_ref();
            let target_machine_ref = build_target_machine_ref(target_ref);
            let orc = LLVMOrcCreateInstance(target_machine_ref);

            let shared_module = LLVMOrcMakeSharedModule(module);
            let resolver_context = Box::new(ResolverContext { orc });
            let resolver_context_ptr = Box::into_raw(resolver_context);

            let mut module_handle: LLVMOrcModuleHandle = 0;
            let result = LLVMOrcAddLazilyCompiledIR(
                orc,
                &mut module_handle,
                shared_module,
                Some(orc_symbol_resolver),
                resolver_context_ptr as *mut libc::c_void
            );

            match result {
                LLVMOrcErrorCode::LLVMOrcErrSuccess => (),
                _ => panic!("Could not add lazily compiled IR module")
            };

            Self {
                compiler,
                orc,
                module_handle,
                shared_module,
                resolver_context: resolver_context_ptr
            }
        }
    }

    pub unsafe fn call_0<R>(&self, method: &CompiledFunction) -> R {
        validate_arg_count(method, 0);
        let function: extern fn() -> R = mem::transmute(self.get_address(method));

        function()
    }

    pub unsafe fn call_1<T1, R>(&self, method: &CompiledFunction, a1: T1) -> R {
        validate_arg_count(method, 1);
        let function: extern fn(T1) -> R = mem::transmute(self.get_address(method));

        function(a1)
    }

    pub unsafe fn call_2<T1, T2, R>(&self, method: &CompiledFunction, a1: T1, a2: T2) -> R {
        validate_arg_count(method, 2);
        let function: extern fn(T1, T2) -> R = mem::transmute(self.get_address(method));

        function(a1, a2)
    }

    pub unsafe fn call_3<T1, T2, T3, R>(&self, method: &CompiledFunction, a1: T1, a2: T2, a3: T3) -> R {
        validate_arg_count(method, 3);
        let function: extern fn(T1, T2, T3) -> R = mem::transmute(self.get_address(method));

        function(a1, a2, a3)
    }

    unsafe fn get_address(&self, method: &CompiledFunction) -> LLVMOrcTargetAddress {
        let mut address: u64 = 0;

        // let mut mangled_symbol = ptr::null_mut() as *mut i8;

        // LLVMOrcGetMangledSymbol(
        //     self.orc,
        //     &mut mangled_symbol,
        //     llvm_utils::llvm_str(&method.name)
        // );

        // println!("Mangled symbol: {:?}", llvm_utils::from_llvm_str(mangled_symbol));

        let result = LLVMOrcGetSymbolAddress(
            self.orc,
            &mut address,
            // mangled_symbol
            llvm_utils::llvm_str(&method.name).as_ptr()
        );

        // LLVMOrcDisposeMangledSymbol(mangled_symbol);

        if address == 0 {
            panic!("Could not find function address");
        }

        match result {
            LLVMOrcErrorCode::LLVMOrcErrSuccess => (),
            _ => panic!("Could not get symbol address in get_address")
        }

        address
    }
}

unsafe fn build_target_ref() -> LLVMTargetRef {
    let target_triple = LLVMGetDefaultTargetTriple();
    let mut target_ref = ptr::null_mut() as LLVMTargetRef;
    let mut error_ptr = ptr::null_mut() as *mut i8;

    let has_error = LLVMGetTargetFromTriple(target_triple, &mut target_ref, &mut error_ptr);

    if has_error == 1 {
        let error_message = CStr::from_ptr(error_ptr).to_str()
            .expect("Cannot decode llvm message");

        panic!(format!("Could not build target ref: {}", error_message));
    }

    if !error_ptr.is_null() {
        LLVMDisposeMessage(error_ptr);
    }

    if LLVMTargetHasJIT(target_ref) == 0 {
        panic!("Cannot do JIT on this platform");
    }

    target_ref
}

unsafe fn build_target_machine_ref(target_ref: LLVMTargetRef) -> LLVMTargetMachineRef {
    let default_triple = LLVMGetDefaultTargetTriple();

    LLVMCreateTargetMachine(
        target_ref,
        default_triple,
        ptr::null(),
        ptr::null(),
        LLVMCodeGenOptLevel::LLVMCodeGenLevelDefault,
        LLVMRelocMode::LLVMRelocDefault,
        LLVMCodeModel::LLVMCodeModelJITDefault
    )
}

extern "C" fn orc_symbol_resolver(
    name: *const i8,
    context: *mut libc::c_void
) -> LLVMOrcTargetAddress {
    unsafe {
        let resolver_context = &*(context as *mut ResolverContext);
        let mut address: LLVMOrcTargetAddress = 0;

        let result = LLVMOrcGetSymbolAddress(
            resolver_context.orc,
            &mut address,
            name
        );

        match result {
            LLVMOrcErrorCode::LLVMOrcErrSuccess => (),
            _ => panic!("Could not get symbol address in orc_symbol_resolver")
        }

        address
    }
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
