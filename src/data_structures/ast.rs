use ::data_structures::core::{Type};

pub enum AST {
    NilLiteral,
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name {
        name: String,

        value_type: Option<Type>
    },

    TypeAssert {
        expr: Box<AST>,
        type_expr: Box<AST>,

        value_type: Option<Type>
    },

    Assignment {
        name: Box<AST>,
        expr: Box<AST>,

        value_type: Option<Type>
    },

    Block(Block),

    MethodCall(MethodCall),

    Branch {
        condition: Box<AST>,
        true_branch: Block,
        false_branch: Block
    },

    Loop {
        condition: Box<AST>,
        body: Block
    },

    Lambda {
        params: Vec<UnparsedMethodParam>,
        body: Block
    },

    StructDef(StructDef),

    ModuleDef(ModuleDef),

    MethodDef(MethodDef),
}

pub struct StructDef {
    pub name: String,
    pub body: Block
}

pub struct ModuleDef {
    pub name: String,
    pub body: Block
}

pub struct MethodDef {
    pub name: String,
    pub params: Vec<UnparsedMethodParam>,
    pub return_type_expr: Box<AST>,

    pub body: Block
}

pub struct UnparsedMethodParam {
    pub name: String,
    pub type_expr: Box<AST>
}

pub struct MethodCall {
    pub target: Box<AST>,
    pub name: String,
    pub args: Vec<AST>,
    pub may_be_var_call: bool,

    pub value_type: Option<Type>
}

pub struct Block {
    pub exprs: Vec<AST>,

    pub value_type: Option<Type>
}
