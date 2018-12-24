use ::core::{Value};

#[derive(Clone)]
pub enum AST {
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },
    StructLiteral(StructLiteral),

    Name { name: String },

    TypeHint(TypeHint),
    Assignment(Assignment),
    Block(Block),
    FnCall(FnCall),
    Branch(Branch),

    // Loop {
    //     condition: Box<AST>,
    //     body: Block
    // },

    ModuleDef(ModuleDef),
    FnDef(FnDef),

    // Used to support partial evaluation
    Value(Value)
}

#[derive(Clone)]
pub struct StructLiteral {
    // pub module: Option<Box<AST>>,
    pub tuples: Vec<(String, Box<AST>)>
}

#[derive(Clone)]
pub struct FnDef {
    pub name: String,
    pub params: Vec<UnparsedFnParam>,
    pub return_type_expr: Box<AST>,

    pub body: Block
}

#[derive(Clone)]
pub struct ModuleDef {
    pub name: Option<String>,
    pub body: Block
}

#[derive(Clone)]
pub struct UnparsedFnParam {
    pub name: String,
    pub type_expr: Box<AST>
}

#[derive(Clone)]
pub struct FnCall {
    pub target: Box<AST>,
    pub name: String,
    pub args: Vec<AST>,
    pub may_be_var_call: bool,
    pub module_resolve: bool
}

#[derive(Clone)]
pub struct Block {
    pub exprs: Vec<AST>
}

#[derive(Clone)]
pub struct Branch {
    pub condition: Box<AST>,
    pub true_branch: Block,
    pub false_branch: Block
}

#[derive(Clone)]
pub struct TypeHint {
    pub expr: Box<AST>,
    pub type_expr: Box<AST>
}

#[derive(Clone)]
pub struct Assignment {
    pub name: Box<AST>,
    pub expr: Box<AST>
}
