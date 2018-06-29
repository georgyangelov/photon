use std::collections::HashMap;

use ::data_structures::{Shared, make_shared, ir, ast};
// use super::constraint_solver::{ConstraintSolver};

struct AnalysisDraft {
    runtime: Shared<ir::Runtime>,
    function_signatures: HashMap<String, ir::FunctionSignature>
}

#[derive(Debug)]
pub enum AnalysisError<'a> {
    CannotHaveExpressionAtTopLevel(&'a ast::AST),
    CannotHaveNestedMethodDefinitions(&'a ast::AST),
    UndefinedVariable(&'a ast::AST),
    UndefinedFunction(&'a ast::AST),
    UnknownType(String)
}

pub fn build_ir(
    runtime: Shared<ir::Runtime>,
    asts: &Vec<ast::AST>
) -> Result<(), AnalysisError> {
    let mut draft = AnalysisDraft {
        runtime: runtime.clone(),

        function_signatures: HashMap::new()
    };

    populate_function_signatures(&mut draft, asts)?;
    build_functions(&mut draft, asts)?;

    Ok(())
}

fn populate_function_signatures<'a>(
    draft: &mut AnalysisDraft,
    asts: &'a Vec<ast::AST>
) -> Result<(), AnalysisError<'a>> {
    for ast in asts {
        match ast {
            &ast::AST::MethodDef(ref method_def) => {
                draft.function_signatures.insert(
                    method_def.name.clone(),
                    build_function_signature(method_def)?
                );
            },

            _ => return Err(AnalysisError::CannotHaveExpressionAtTopLevel(ast))
        };
    }

    Ok(())
}

fn build_function_signature<'a>(
    def: &'a ast::MethodDefAST
) -> Result<ir::FunctionSignature, AnalysisError<'a>> {
    let mut param_types = Vec::new();

    for param in &def.params {
        param_types.push(make_shared(ir::Variable {
            name: param.name.clone(),
            value_type: resolve_type(&param.kind)?
        }));
    }

    Ok(ir::FunctionSignature {
        return_type: resolve_type(&def.return_kind)?,
        params: param_types
    })
}

fn build_functions<'a>(
    draft: &mut AnalysisDraft,
    asts: &'a Vec<ast::AST>
) -> Result<(), AnalysisError<'a>> {
    for ast in asts {
        let function = match ast {
            &ast::AST::MethodDef(ref method_def) => build_function(draft, method_def)?,

            _ => return Err(AnalysisError::CannotHaveExpressionAtTopLevel(ast))
        };

        draft.runtime.borrow_mut().functions.insert(function.name.clone(), make_shared(function));
    }

    Ok(())
}

fn build_function<'a>(
    draft: &mut AnalysisDraft,
    ast: &'a ast::MethodDefAST
) -> Result<ir::Function, AnalysisError<'a>> {
    let mut scope = ir::Scope {
        runtime: draft.runtime.clone(),
        vars: HashMap::new()
    };

    let signature = draft.function_signatures.get(&ast.name)
        .expect(&format!("Signature for {} did not exist in the analysis draft", &ast.name))
        .clone();

    let name = ast.name.clone();

    for param in &signature.params {
        scope.vars.insert(name.clone(), param.clone());
    }

    let code = build_block(&ast.body, make_shared(scope))?;

    let implementation = ir::FunctionImpl::Photon(ir::PhotonFunction {
        code
    });

    Ok(ir::Function {
        name,
        signature,
        implementation
    })
}

fn build_block<'a>(
    block_ast: &'a ast::BlockAST,
    scope: Shared<ir::Scope>
) -> Result<ir::Block, AnalysisError<'a>> {
    if block_ast.catches.len() > 0 {
        panic!(format!("Block catch clauses are not supported: {:?}", &block_ast));
    }

    let mut code = Vec::new();

    for expr in &block_ast.exprs {
        let instruction = build_instruction(expr, scope.clone())?;

        code.push(ir::IR { instruction });
    }

    Ok(ir::Block { code })
}

fn build_instruction<'a>(
    expr: &'a ast::AST,
    scope: Shared<ir::Scope>
) -> Result<ir::Instruction, AnalysisError<'a>> {
    let instruction = match expr {
        &ast::AST::NilLiteral             => ir::Instruction::Literal(ir::ConstValue::Nil),
        &ast::AST::BoolLiteral  { value } => ir::Instruction::Literal(ir::ConstValue::Bool(value)),
        &ast::AST::IntLiteral   { value } => ir::Instruction::Literal(ir::ConstValue::Int(value)),
        &ast::AST::FloatLiteral { value } => ir::Instruction::Literal(ir::ConstValue::Float(value)),

        &ast::AST::Name { ref name, .. } => {
            let scope = scope.borrow();
            let maybe_variable = scope.vars.get(name);

            if let Some(variable) = maybe_variable {
                ir::Instruction::VariableRef(ir::VariableRef {
                    variable: variable.clone()
                })
            } else {
                return Err(AnalysisError::UndefinedVariable(expr));
            }
        },

        // TODO: Use `may_be_var_call`
        &ast::AST::MethodCall(ref call) => {
            let mut args = Vec::new();

            let self_arg = build_instruction(&call.target, scope.clone())?;
            args.push(ir::IR { instruction: self_arg });

            for arg in &call.args {
                let arg_instruction = build_instruction(arg, scope.clone())?;
                args.push(ir::IR { instruction: arg_instruction });
            }

            let function = {
                let scope = scope.borrow();
                let runtime = scope.runtime.borrow();

                match runtime.functions.get(&call.name) {
                    Some(f) => f.clone(),
                    None => return Err(AnalysisError::UndefinedFunction(expr))
                }
            };

            ir::Instruction::Call(ir::Call { function, args })
        },

        &ast::AST::MethodDef(_) => {
            return Err(AnalysisError::CannotHaveNestedMethodDefinitions(expr));
        },

        _ => panic!(format!("Unsupported expression type in build_block: {:?}", expr))
    };

    Ok(instruction)
}

fn resolve_type<'a, 'b>(kind: &'a str) -> Result<ir::Type, AnalysisError<'b>> {
    match kind {
        "Nil"   => Ok(ir::Type::Nil),
        "Bool"  => Ok(ir::Type::Bool),
        "Int"   => Ok(ir::Type::Int),
        "Float" => Ok(ir::Type::Float),

        _ => return Err(AnalysisError::UnknownType(String::from(kind)))
    }
}
